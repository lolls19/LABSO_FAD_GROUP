import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Questa e' la tabella condivisa dell'aggregatore: tiene traccia di tutti i nodi
 * sensore che si sono mai registrati (con il loro InfoPeer) e, di conseguenza, di
 * chi possiede quali rilevazioni. E' la risorsa piu' importante e delicata di
 * tutto il progetto, perche' viene letta e modificata contemporaneamente da tutti
 * i thread che gestiscono le connessioni dei vari nodi (un thread per nodo, vedi
 * GestoreNodo).
 *
 * Per gestire questo accesso concorrente la classe implementa a mano un lock
 * lettori-scrittori: piu' operazioni di sola lettura (per esempio elencare le
 * rilevazioni) possono avvenire insieme senza problemi, mentre un'operazione di
 * scrittura (per esempio registrare un nuovo nodo) ha bisogno di accesso
 * esclusivo, cioe' deve aspettare che tutte le letture in corso finiscano e deve
 * bloccare quelle nuove finche' non ha finito. Questo permette piu' concorrenza
 * rispetto a un semplice "synchronized" su tutti i metodi, dato che le letture
 * sono molto piu' frequenti delle scritture.
 */
public class TabellaRilevazioni {

    private final Map<String, InfoPeer> peers = new HashMap<>();

    private int peerCont = 0;

    private int lettori = 0;
    private boolean scrittura = false;

    public TabellaRilevazioni() {
        this.peerCont = 0;
    }

    // Fa entrare un lettore: se c'e' una scrittura in corso lo mette in attesa (rilasciando il
    // lock mentre aspetta), altrimenti incrementa subito il contatore dei lettori attivi. Il
    // controllo e' dentro un while e non un if perche' al risveglio bisogna sempre riverificare
    // che la condizione sia ancora valida.
    private synchronized void startRead() {
        while (scrittura) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lettori++;
    }

    // Segnala che un lettore ha finito: se era l'ultimo rimasto, sveglia tutti i thread in attesa
    // (potrebbe essere il turno di uno scrittore che stava aspettando che le letture finissero).
    private synchronized void endRead() {
        lettori--;
        if (lettori == 0) {
            notifyAll();
        }
    }

    // Fa entrare uno scrittore: puo' procedere solo se non ci sono ne' letture ne' scritture in
    // corso, altrimenti resta in attesa. Una volta ottenuto il turno, blocca tutti gli altri
    // finche' non avra' finito e chiamato endWrite().
    private synchronized void startWrite() {
        while (scrittura || lettori > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        scrittura = true;
    }

    // Segnala che la scrittura e' terminata e sveglia tutti i thread in attesa, sia i lettori sia
    // gli eventuali altri scrittori in coda.
    private synchronized void endWrite() {
        scrittura = false;
        notifyAll();
    }

    // Registra un nuovo nodo sensore: gli assegna un identificativo progressivo (peer0, peer1,
    // ...), crea il suo InfoPeer con le rilevazioni iniziali dichiarate e lo inserisce in tabella.
    // Restituisce l'id appena assegnato, che il chiamante usera' per riconoscere il nodo da qui in
    // avanti.
    public String registraNodo(String host, int port, List<String> rilevazioni) {
        startWrite();
        try {
            String peerId = "peer" + (peerCont++);
            InfoPeer info = new InfoPeer(peerId, host, port);
            for (String r : rilevazioni) {
                if (!r.isBlank()) {
                    info.addRilevazione(r.trim());
                }
            }
            peers.put(peerId, info);
            return peerId;
        } finally {
            endWrite();
        }
    }

    // Aggiunge una rilevazione a un nodo gia' registrato: viene chiamato sia quando un nodo
    // annuncia una nuova risorsa propria, sia quando un download va a buon fine e il richiedente
    // diventa a sua volta possessore della rilevazione scaricata. Se il nodo non esiste (caso
    // anomalo) semplicemente non fa nulla.
    public void addRilevazione(String peerId, String rilevazione) {
        startWrite();
        try {
            InfoPeer info = peers.get(peerId);
            if (info != null) {
                info.addRilevazione(rilevazione);
            }
        } finally {
            endWrite();
        }
    }

    // Toglie una rilevazione da un nodo: viene usato quando un download fallisce perche' il nodo
    // scelto non possedeva piu' davvero quella rilevazione, cosi' che non venga riproposto in
    // futuro per la stessa risorsa.
    public void removeEntryRilevazione(String peerId, String rilevazione) {
        startWrite();
        try {
            InfoPeer info = peers.get(peerId);
            if (info != null) {
                info.removeRilevazione(rilevazione);
            }
        } finally {
            endWrite();
        }
    }

    // Marca un nodo come offline, tipicamente quando si disconnette (in modo ordinato o per
    // caduta della connessione). Il nodo resta comunque nella tabella con le sue rilevazioni: non
    // viene piu' proposto come fornitore per i download, ma non scompare dagli elenchi.
    public void markOffline(String peerId) {
        startWrite();
        try {
            InfoPeer info = peers.get(peerId);
            if (info != null) {
                info.setOnline(false);
            }
        } finally {
            endWrite();
        }
    }

    // Costruisce una mappa "rilevazione -> lista dei nodi che la possiedono", usata dai comandi
    // listdata/LIST. Non filtra i nodi offline apposta: anche dopo un quit, le rilevazioni di quel
    // nodo restano visibili in questo elenco (anche se poi non sono davvero scaricabili, perche'
    // selectProvider le esclude).
    public Map<String, List<String>> listaRilevazioni() {
        startRead();
        try {
            Map<String, List<String>> risultato = new LinkedHashMap<>();
            for (InfoPeer p : peers.values()) {
                for (String r : p.getSnapshotRilevazione()) {
                    if (!risultato.containsKey(r)) {
                        risultato.put(r, new ArrayList<>());
                    }
                    risultato.get(r).add(p.getPeerId());
                }
            }
            return risultato;
        } finally {
            endRead();
        }
    }

    // Restituisce la lista degli id dei nodi attualmente online, cioe' quelli che si possono
    // ancora considerare raggiungibili per un download.
    public List<String> activeNodes() {
        startRead();
        try {
            List<String> nodiOnline = new ArrayList<>();
            for (InfoPeer p : peers.values()) {
                if (p.isOnline()) {
                    nodiOnline.add(p.getPeerId());
                }
            }
            return nodiOnline;
        } finally {
            endRead();
        }
    }

    // Cerca un nodo online che possieda la rilevazione richiesta e che non sia gia' tra quelli
    // esclusi (cioe' gia' provati senza successo). Restituisce il primo che trova, oppure null se
    // nessun nodo puo' fornire quella rilevazione: e' cosi' che l'aggregatore decide a chi
    // proporre un download e come gestisce i tentativi falliti (RETRY).
    public InfoPeer selectProvider(String rilevazione, Set<String> excluded) {
        startRead();
        try {
            for (InfoPeer p : peers.values()) {
                if (p.isOnline() && p.hasRilevazione(rilevazione) && !excluded.contains(p.getPeerId())) {
                    return p;
                }
            }
            return null;
        } finally {
            endRead();
        }
    }
}
