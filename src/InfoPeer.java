import java.util.HashSet;
import java.util.Set;

/*
 * Questa classe rappresenta un nodo sensore cosi' come lo conosce l'aggregatore:
 * il suo identificativo, l'indirizzo e la porta a cui contattarlo, quali
 * rilevazioni possiede e se al momento e' online oppure no. E' sostanzialmente
 * la "scheda anagrafica" di un peer, tenuta dentro TabellaRilevazioni.
 *
 * Dato che piu' thread diversi (uno per ogni connessione gestita dall'aggregatore)
 * possono leggere e modificare lo stato di uno stesso InfoPeer contemporaneamente,
 * il campo "online" e' volatile (basta per un singolo valore letto/scritto da piu'
 * thread) mentre l'insieme delle rilevazioni, che invece subisce piu' operazioni
 * (aggiungi, rimuovi, controlla), e' protetto con synchronized.
 */
public class InfoPeer {

    private final String peerId;
    private final String host;
    private final int port;

    private final Set<String> rilevazioni = new HashSet<>();

    private volatile boolean online = true;

    // Costruttore: salva semplicemente i dati del nodo cosi' come arrivano dalla registrazione.
    public InfoPeer(String peerId, String host, int port) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
    }

    // Getter dei dati identificativi del nodo: non serve sincronizzarli perche' sono campi final,
    // quindi non cambiano mai dopo la creazione dell'oggetto.
    public String getPeerId() { return peerId; }
    public String getHost()   { return host; }
    public int    getPort()   { return port; }

    // Permettono di leggere e cambiare lo stato online/offline del nodo. Il campo e' volatile
    // proprio per garantire che ogni thread veda subito il valore aggiornato dagli altri.
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    // Gestiscono l'insieme delle rilevazioni possedute dal nodo: aggiungerne una, toglierne una,
    // controllare se e' presente. Sono synchronized perche' l'insieme puo' essere usato da piu'
    // thread nello stesso momento.
    public synchronized void addRilevazione(String r)    { rilevazioni.add(r); }
    public synchronized void removeRilevazione(String r) { rilevazioni.remove(r); }
    public synchronized boolean hasRilevazione(String r)  { return rilevazioni.contains(r); }

    // Restituisce una copia dell'insieme delle rilevazioni, non l'insieme originale: chi la
    // riceve puo' scorrerla o modificarla senza rischiare di alterare lo stato interno del nodo
    // o di causare errori di concorrenza se nel frattempo qualcun altro lo sta modificando.
    public synchronized Set<String> getSnapshotRilevazione() {
        return new HashSet<>(rilevazioni);
    }
}
