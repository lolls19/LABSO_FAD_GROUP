
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 Tabella delle rilevazioni possedute dai nodi sensore.
 E' LA risorsa condivisa centrale dell'aggregatore, vi accedono
 concorrentemente tutti i thread ClientHandler.
 
 */
public class TabellaRilevazioni {

    // Mappa identificativo , dati del nodo
    //  ogni accesso e' gia' protetto dal lock lettori-scrittori sottostante.
    private final Map<String, InfoPeer> peers = new HashMap<>();

    // Contatore progressivo per assegnare identificativi univoci 
    private int peerCont = 0;

    private int lettori = 0;            // quante letture sono in corso in questo momento
    private boolean scrittura = false;  // true se c'e' una scrittura in corso

    public TabellaRilevazioni() {
        this.peerCont = 0;
    }

    // LOCK LETTORI-SCRITTORI 
    // metodo chiamto quando un lettore vuole entrare
    private synchronized void startRead() {
        // Se c'e' una scrittura in corso, il lettore aspetta.
        // il while permette che la condizione venga ricontrollata dopo il risveglio 
        while (scrittura) {
            try {
                wait(); // il wait permette di rilasciare il lock e sospendere il thread finché non viene notificato
            } catch (InterruptedException e) {
                // Se il thread viene interrotto mentre è in attesa, ripristina lo stato di interruzione
                Thread.currentThread().interrupt();
            }
        }
        lettori++; // se non c'e' scrittura in corso, il lettore entra e incrementa il contatore dei lettori
    }

    // metodo che viene chiamato quando un lettore ha finito di leggere
    private synchronized void endRead() {
        lettori--;
        if (lettori == 0) {
            // nel caso in cui non ci sono più lettori, notifica tutti i thread in attesa 
            notifyAll();
        }
    }

    // metodo chiamato quando uno scrittore vuole entrare
    private synchronized void startWrite() {
        // puo entrare solo se non ci sono lettori e non c'e' una scrittura in corso
        while (scrittura || lettori > 0) {
            try {
                wait(); //rialscia il lock e sospende il thread finché non viene notificato
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        scrittura = true; // prende il lock di scrittura, nessun altro lettore o scrittore puo' entrare
    }

    // metodo che viene chiamato quando uno scrittore ha finito di scrivere
    private synchronized void endWrite() {
        scrittura = false;   // rilascia il lock di scrittura
        notifyAll();         // risveglia tutti i thread in attesa, sia lettori che scrittori
    }

    // Metodi per la scrittura (modifica della tabella)
    // tutti i metodi che modificano la tabella devono essere protetti da startWrite() e endWrite()
    // il finally garantisce che il lock venga rilasciato anche in caso di errore
    
     //metodo che registra un nuovo nodo sensore nella tabella, con le rilevazioni iniziali.
    public String registraNodo(String host, int port, List<String> rilevazioni) {
        startWrite(); // accesso esclusivo
        try {
            String peerId = "peer" + (peerCont++);        // id univoco
            InfoPeer info = new InfoPeer(peerId, host, port); // inizializza con i dati del nodo
            for (String r : rilevazioni) {                   // inserisco le rilevazioni iniziali
                if (!r.isBlank()) {                         
                    info.addRilevazione(r.trim());            
                }
            }
            peers.put(peerId, info);                       // inserisco il nodo nella mappa
            return peerId;                                // ritorna l'id
        } finally {
            endWrite();                                    // rilascia il lock di scrittura anche in caso di eccezione
        }
    }

    // metodo che aggiunge una rilevazione per un nodo viene chiamato quando un nodo ha scaricato con successo una rilevazione da un altro nodo
    public void addRilevazione(String peerId, String rilevazione) {
        startWrite();
        try {
            InfoPeer info = peers.get(peerId);   // cerca il nodo
            // se il nodo esiste, aggiunge la rilevazione alla sua lista di rilevazioni
            if (info != null) {                  
                info.addRilevazione(rilevazione);      
            }
        } finally {
            endWrite();
        }
    }

    // metodo che rimuove una rilevazione per un nodo, viene chiamato quando si vuole rimuovere una rilevazione da un nodo
    
    public void removeEntryRilevazione(String peerId, String rilevazione) {
        startWrite();
        try {
            InfoPeer info = peers.get(peerId);
            // Se il nodo esiste, rimuove la rilevazione dalla sua lista di rilevazioni
            if (info != null) {
                info.removeRilevazione(rilevazione);   
            }
        } finally {
            endWrite();
        }
    }

    // metodo che marca un nodo come offline, viene chiamato quando si vuole rimuovere un nodo dalla lista dei nodi attivi
    // quindi non verra' piu' considerato per i download futuri
    public void markOffline(String peerId) {
        startWrite(); // accesso esclusivo per modificare lo stato del nodo
        try {
            // cerca il nodo nella mappa 
            InfoPeer info = peers.get(peerId);
            // se il nodo esiste, lo marca come offline
            if (info != null) {
                info.setOnline(false);           
            }
        } finally {
            endWrite();
        }
    }

    // Metodi per la lettura (accesso alla tabella)
    // tutti i metodi che leggono la tabella devono essere protetti da startRead
    // Piu' letture possono girare in parallelo tra loro.
    

    // metodo che restituisce una mappa delle rilevazioni possedute dai nodi online, con la lista dei nodi che le possiedono
    public Map<String, List<String>> listaRilevazioni() {
        startRead(); // accesso condiviso
        try {
            // Si crea una linkedhashmap per mantenere l'ordine di inserimento delle rilevazioni
            Map<String, List<String>> risultato = new LinkedHashMap<>();
            // per ogni nodo nella tabella, se e' online, si prende la lista delle sue rilevazioni e si aggiunge alla mappa risultato
            for (InfoPeer p : peers.values()) {      
                if (!p.isOnline()) {
                    continue;                        
                }
                // si itera sulle rilevazioni del nodo online, e per ogni rilevazione si aggiunge il nodo alla lista dei possessori
                for (String r : p.getSnapshotRilevazione()) { // si utilizza la copia della lista delle rilevazioni per evitare modifiche concorrenti
                    // se è la prima volta che si incontra questa rilevazione, si crea una nuova lista vuota per i nodi che la possiedono
                    if (!risultato.containsKey(r)) {    
                        risultato.put(r, new ArrayList<>()); 
                    }
                    risultato.get(r).add(p.getPeerId()); // altrimenti si aggiunge il nodo alla lista dei possessori della rilevazione
                }
            }
            return risultato;
        } finally {
            endRead(); // rilascia il lock di lettura
        }
    }

    // metodo che restituisce la lista dei nodi online, viene chiamato quando si vuole sapere quali nodi sono attivi
    public List<String> activeNodes() {
        startRead();// accesso condiviso
        try {
            //si crea la lista dove si aggiungono i nodi online
            List<String> nodiOnline = new ArrayList<>();
            for (InfoPeer p : peers.values()) {
                if (p.isOnline()) {          // solo i nodi online
                    nodiOnline.add(p.getPeerId());
                }
            }
            return nodiOnline;// ritorna la lista dei nodi online
        } finally {
            endRead();
        }
    }

    // metodo che seleziona un nodo online che possiede una rilevazione, escludendo quelli gia' scartati.
    // si utilizza per trovare un nodo da cui scaricare una rilevazione precisa, evitando di ripetere i nodi che hanno gia' fallito il download
    public InfoPeer selectProvider(String rilevazione, Set<String> excluded) {
        startRead();// accesso condiviso
        try {
            // si itera sui nodi nella tabella, e si cerca il primo nodo che soddisfa le condizioni: online, possiede la rilevazione, non e' gia' stato escluso
            for (InfoPeer p : peers.values()) {
                if (p.isOnline() && p.hasRilevazione(rilevazione) && !excluded.contains(p.getPeerId())) {
                    return p; // ritorna il nodo trovato, altrimenti continua a cercare
                }
            }
            return null; // nel caso non ci siano nodi che soddisfano le condizioni, ritorna null, rilevando che non ci sono rilevazioni disponibili per il download
        } finally {
            endRead();
        }
    }
}
