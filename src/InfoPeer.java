import java.util.HashSet;
import java.util.Set;

/*
 * Questa classe rappresenta un nodo sensore cosi' come lo conosce l'aggregatore:
 * il suo identificativo, l'indirizzo e la porta a cui contattarlo, quali
 * rilevazioni possiede e se al momento e' online oppure no. Quindi contiene le 
 * informazioni che l'aggregatore deve mantenere per ogni nodo registrato, e fornisce
 * metodi per leggerle e aggiornarle.
 *
 * Dato che piu' thread diversi (uno per ogni connessione gestita dall'aggregatore)
 * possono leggere e modificare lo stato di uno stesso InfoPeer contemporaneamente,
 * il campo "online" e' volatile (basta per un singolo valore letto/scritto da piu'
 * thread) mentre l'insieme delle rilevazioni, che invece subisce piu' operazioni
 * (aggiungi, rimuovi, controlla), e' protetto con synchronized perchè si deve garantire
 * l'accesso esclusivo agli elementi dell'insieme da parte di un solo thread alla volta.
 */
public class InfoPeer {

    private final String peerId;
    private final String host;
    private final int port;

    private final Set<String> rilevazioni = new HashSet<>();

    private volatile boolean online = true;

    // Metodo costruttore che inizializza l'oggetto con i dati identificativi del nodo: id, host e porta.
    public InfoPeer(String peerId, String host, int port) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
    }

    // i metodi getter restituiscono i dati identificativi del nodo: id, host e porta.
    public String getPeerId() { return peerId; }
    public String getHost()   { return host; }
    public int    getPort()   { return port; }

    // Metodi per leggere e modificare lo stato online/offline del nodo: sono sincronizzati perche' piu' thread
    // diversi possono leggere e modificare lo stato di uno stesso nodo contemporaneamente.
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    // Metodi per leggere e modificare l'insieme delle rilevazioni possedute dal nodo: sono sincronizzati
    // perche' piu' thread diversi possono leggere e modificare lo stato di uno stesso nodo contemporaneamente.
    public synchronized void addRilevazione(String r)    { rilevazioni.add(r); }
    public synchronized void removeRilevazione(String r) { rilevazioni.remove(r); }
    public synchronized boolean hasRilevazione(String r)  { return rilevazioni.contains(r); }

    // metodo per ottenere una copia dell'insieme delle rilevazioni possedute dal nodo.
    // E' sincronizzato perche' piu' thread diversi possono leggere e modificare lo stato
    //  di uno stesso nodo contemporaneamente. Lo restituisce come un nuovo HashSet, cosi' 
    // chi lo riceve non va a mdofidcare l'hashSet originale, ma lavora su una copia.
    public synchronized Set<String> getSnapshotRilevazione() {
        return new HashSet<>(rilevazioni);
    }
}
