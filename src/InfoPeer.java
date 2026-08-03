import java.util.HashSet;
import java.util.Set;

/*la classe descrive il nodo sensore così come conosciuto dall'aggregatore.
  Contiene identificativo, indirizzo, porta e stato del peer.

  online è volatile: singola variabile, accesso atomico (letto/scritto
   da thread diversi), non serve un lock per garantirne la visibilità

  rilevazioni è protetto da synchronized, collezione con operazioni
  multiple (aggiungi/rimuovi/leggi) quindi serve mutua esclusione
  */ 
public class InfoPeer {

    private final String peerId;   
    private final String host;     
    private final int port;        

    // set di rilevazioni dichiarate dal peer, protetto da synchronized
    private final Set<String> rilevazioni = new HashSet<>();

    // stato di connessione del peer, volatile per accesso atomico
    private volatile boolean online = true;

    public InfoPeer(String peerId, String host, int port) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
    }

    // getter per i campi finali, non serve synchronized perché sono immutabili
    public String getPeerId() { return peerId; }
    public String getHost()   { return host; }
    public int    getPort()   { return port; }

   // get e set per online, volatile garantisce visibilità tra thread
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    // sono synchronized perché accedono a resources, che è una collezione condivisa
    public synchronized void addRilevazione(String r)    { rilevazioni.add(r); }
    public synchronized void removeRilevazione(String r) { rilevazioni.remove(r); }
    public synchronized boolean hasRilevazione(String r)  { return rilevazioni.contains(r); }

    // restituisce una copia dello stato delle risorse, per evitare modifiche concorrenti
    public synchronized Set<String> getSnapshotRilevazione() {
        return new HashSet<>(rilevazioni);
    }
}