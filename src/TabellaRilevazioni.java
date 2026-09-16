import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * Questa e' la tabella condivisa dell'aggregatore: tiene traccia di tutti i nodi
 * sensore che si sono mai registrati (con il loro InfoPeer) e, di conseguenza, di
 * chi possiede quali rilevazioni. Viene letta e modificata contemporaneamente da tutti
 * i thread che gestiscono le connessioni dei vari nodi (un thread per nodo).
 *
 * Per gestire questo accesso concorrente la classe implementa un lock
 * lettori-scrittori: piu' operazioni di sola lettura (per esempio elencare le
 * rilevazioni) possono avvenire insieme senza problemi, mentre un'operazione di
 * scrittura (per esempio registrare un nuovo nodo) ha bisogno di accesso
 * esclusivo, cioe' deve aspettare che tutte le letture in corso finiscano e deve
 * bloccare quelle nuove finche' non ha finito. In questo modo ogni richiesta che
 * arriva all'aggregatore mentre la tabella viene aggiornata resta in attesa finche'
 * l'aggiornamento non e' completato.
 * Il lock da' la precedenza agli scrittori: appena uno scrittore si mette in attesa,
 * i nuovi lettori non possono piu' entrare, cosi' un flusso continuo di letture non
 * puo' rimandare all'infinito un aggiornamento della tabella (starvation degli scrittori).
 */
public class TabellaRilevazioni {
    // Mappa "peerId -> InfoPeer" che contiene tutti i nodi registrati, sia online sia offline.
    // E' una LinkedHashMap cosi' i nodi vengono sempre scorsi nell'ordine di registrazione.
    private final Map<String, InfoPeer> peers = new LinkedHashMap<>();

    private int peerCont = 0;

    private int lettori = 0;
    private int scrittoriInAttesa = 0;
    private boolean scrittura = false;
 // metodo costruttore che inizializza il contatore dei peer a 0, quindi la tabella e' vuota all'inizio.
    public TabellaRilevazioni() {
        this.peerCont = 0;
    }

    /*Implementazione del lock lettori-scrittori */

    // Fa entrare un lettore: puo' procedere solo se non c'e' uno scrittore in corso e nessuno scrittore
    // in attesa, altrimenti resta in attesa. Una volta ottenuto il turno puo' leggere insieme ad
    // altri lettori, finche' non chiama endRead().
    // Se il thread viene interrotto mentre aspetta, l'interruzione viene ricordata e ripristinata solo
    // dopo aver ottenuto il turno: richiamare subito interrupt() dentro il ciclo farebbe lanciare di
    // nuovo l'eccezione alla wait() successiva, e il thread girerebbe a vuoto senza mai fermarsi.
    private synchronized void startRead() {
        boolean interrotto = false;
        while (scrittura || scrittoriInAttesa > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrotto = true;
            }
        }
        lettori++;
        if (interrotto) {
            Thread.currentThread().interrupt();
        }
    }

    // metodo che segna la fine della lettura da parte di un thread: decrementa il contatore dei lettori e,
    // se non ce ne sono piu', sveglia tutti i thread in attesa (gli scrittori che aspettavano la fine
    // delle letture in corso).
    private synchronized void endRead() {
        lettori--;
        if (lettori == 0) {
            notifyAll();
        }
    }

    // metodo che fa entrare uno scrittore: si registra come scrittore in attesa (bloccando l'ingresso di
    // nuovi lettori) e puo' procedere solo quando non c'e' nessun lettore in corso e nessun altro scrittore.
    // Una volta ottenuto il turno, puo' scrivere e nessun altro lettore o scrittore puo' entrare
    // finche' non chiama endWrite(). L'interruzione viene gestita come in startRead().
    private synchronized void startWrite() {
        boolean interrotto = false;
        scrittoriInAttesa++;
        while (scrittura || lettori > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrotto = true;
            }
        }
        scrittoriInAttesa--;
        scrittura = true;
        if (interrotto) {
            Thread.currentThread().interrupt();
        }
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
    // dato che scrive in tabella utilizza i metodi startWrite() e endWrite() per garantire l'accesso esclusivo alla tabella durante la scrittura.
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
    // anomalo) non fa nulla.
    //dato che scrive in tabella utilizza i metodi startWrite() e endWrite() per garantire l'accesso esclusivo alla tabella durante la scrittura.
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
    // futuro per la stessa risorsa, e quando un nodo chiede di scaricare una rilevazione che quindi
    // non possiede in locale.
    // dato che scrive in tabella utilizza i metodi startWrite() e endWrite() per garantire l'accesso esclusivo alla tabella durante la scrittura.
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

    // Marca un nodo come offline, tipicamente quando si disconnette (in modo voluto o per
    // caduta della connessione). Il nodo resta comunque nella tabella con le sue rilevazioni: non
    // viene piu' proposto come fornitore per i download, ma non scompare dagli elenchi.
    // dato che scrive in tabella utilizza i metodi startWrite() e endWrite() per garantire l'accesso esclusivo alla tabella durante la scrittura.
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

    // Costruisce una mappa dove ogni rilevazione e' collegata alla lista dei nodi che la possiedono, usata dai comandi
    // listdata/LIST. Le rilevazioni sono ordinate per nome (TreeMap). Non filtra i nodi offline: anche dopo un quit,
    // le rilevazioni di quel nodo restano visibili in questo elenco (anche se poi non sono davvero scaricabili).
    // dato che legge la tabella utilizza i metodi startRead() e endRead(): altri lettori possono leggere insieme,
    // ma nessuno scrittore puo' modificare la tabella finche' la lettura non e' finita.
    public Map<String, List<String>> listaRilevazioni() {
        startRead();
        try {
            Map<String, List<String>> risultato = new TreeMap<>();
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

    // Restituisce i nodi attualmente online, cioe' quelli che si possono ancora considerare
    // raggiungibili, escluso il nodo indicato (il nodo che ha fatto la richiesta, che vuole
    // conoscere gli altri nodi attivi). Usato dal comando NODES.
    // dato che legge la tabella utilizza i metodi startRead() e endRead().
    public List<InfoPeer> activeNodes(String escluso) {
        startRead();
        try {
            List<InfoPeer> nodiOnline = new ArrayList<>();
            for (InfoPeer p : peers.values()) {
                if (p.isOnline() && !p.getPeerId().equals(escluso)) {
                    nodiOnline.add(p);
                }
            }
            return nodiOnline;
        } finally {
            endRead();
        }
    }

    // Restituisce tutti i nodi che secondo la tabella possiedono la rilevazione indicata, sia online
    // sia offline (il chiamante puo' distinguerli con isOnline()). Usato dal comando WHOHAS.
    // dato che legge la tabella utilizza i metodi startRead() e endRead().
    public List<InfoPeer> nodiConRilevazione(String rilevazione) {
        startRead();
        try {
            List<InfoPeer> possessori = new ArrayList<>();
            for (InfoPeer p : peers.values()) {
                if (p.hasRilevazione(rilevazione)) {
                    possessori.add(p);
                }
            }
            return possessori;
        } finally {
            endRead();
        }
    }

    // Cerca un nodo online che possieda la rilevazione richiesta e che non sia gia' tra quelli
    // esclusi (cioe' gia' provati senza successo, oppure il richiedente stesso). Restituisce il primo
    // che trova, oppure null se nessun nodo puo' fornire quella rilevazione: e' cosi' che l'aggregatore
    // decide a chi proporre un download e come gestisce i tentativi falliti (RETRY).
    // dato che legge la tabella utilizza i metodi startRead() e endRead().
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
