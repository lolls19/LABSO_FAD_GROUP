import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/*
 * Questa classe gestisce l'unica connessione persistente che il nodo mantiene
 * verso l'aggregatore per tutta la sua vita: registrazione iniziale, notifica di
 * nuove rilevazioni, richiesta degli elenchi (rilevazioni di rete, nodi attivi, nodi
 * che possiedono una rilevazione), e tutti i messaggi legati al download (richiesta
 * del token, retry, conferma finale). Sia la console interattiva sia il Downloader
 * usano questo stesso oggetto per parlare con l'aggregatore, quindi i metodi che
 * inviano una richiesta e ne leggono la risposta sono synchronized: cosi' una
 * richiesta e la sua risposta viaggiano sempre una alla volta sullo stesso
 * socket, senza che i messaggi di due chiamate diverse si mescolino.
 * Se l'aggregatore chiude la connessione (per esempio perche' e' stato arrestato),
 * la lettura della risposta restituisce null: in quel caso viene lanciata una
 * IOException, cosi' il comando in corso termina con un messaggio di errore invece
 * di proseguire con una risposta inesistente.
 */
public class AggregatorLink implements Closeable {

    private final Socket socket;
    private final BufferedReader lettore;
    private final PrintWriter scrittore;
    private String peerId;
    private volatile boolean disconnesso = false;

    /*
     * Apre la connessione verso l'aggregatore e prepara i flussi di lettura e scrittura testuali
     * in UTF-8. Se qualcosa va storto durante l'inizializzazione, chiude subito il socket per non
     * lasciarlo aperto inutilmente e rilancia l'eccezione.
     */
    public AggregatorLink(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        try {
            this.lettore = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.scrittore = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /*
     * Restituisce l'id assegnato dall'aggregatore durante la registrazione (null se il nodo non si
     * e' ancora registrato).
     */
    public synchronized String getPeerId() {
        return peerId;
    }

    /*
     * Restituisce l'indirizzo IP locale con cui questo nodo puo' essere raggiunto dagli altri
     * peer, usato durante la registrazione.
     */
    public String localAddress() {
        return socket.getLocalAddress().getHostAddress();
    }

    /*
     * Legge una riga di risposta dall'aggregatore. Se la connessione e' stata chiusa (readLine
     * restituisce null) lancia una IOException, cosi' chi chiama non deve controllare ogni volta.
     */
    private String leggiRisposta() throws IOException {
        String riga = lettore.readLine();
        if (riga == null) {
            throw new IOException("connessione con l'aggregatore persa");
        }
        return riga;
    }

    /*
     * Invia una richiesta il cui risultato e' un elenco (LIST, NODES, WHOHAS) e restituisce le righe
     * ricevute cosi' come sono, leggendo finche' non arriva il separatore di fine elenco.
     */
    private List<String> richiediElenco(String richiesta) throws IOException {
        scrittore.println(richiesta);
        List<String> righe = new ArrayList<>();
        String riga;
        while (!(riga = leggiRisposta()).equals(Protocol.END)) {
            if (riga.startsWith(Protocol.ERR)) {
                throw new IOException("richiesta rifiutata dall'aggregatore: " + riga);
            }
            righe.add(riga);
        }
        return righe;
    }

    /*
     * Registra questo nodo presso l'aggregatore, comunicando il proprio indirizzo, la porta P2P e
     * le rilevazioni possedute all'avvio (oppure "-" se non ne ha nessuna). Controlla che la
     * risposta sia effettivamente un OK con un id valido prima di accettarla, altrimenti solleva
     * un'eccezione; se tutto va bene salva l'id assegnato e lo restituisce.
     */
    public synchronized String register(String peerHost, int peerPort, List<String> rilevazioni) throws IOException {
        String ril = (rilevazioni == null || rilevazioni.isEmpty()) ? "-" : String.join(",", rilevazioni);
        scrittore.println(Protocol.REGISTER + " " + peerHost + " " + peerPort + " " + ril);

        String riga = leggiRisposta();
        if (!riga.startsWith(Protocol.OK)) {
            throw new IOException("Errore nella registrazione: " + riga);
        }

        String[] campi = riga.split("\\s+");
        if (campi.length < 2) {
            throw new IOException("Risposta dell'aggregatore non valida: " + riga);
        }

        this.peerId = campi[1];
        return this.peerId;
    }

    /*
     * Avvisa l'aggregatore che il nodo ha aggiunto una nuova rilevazione e aspetta la conferma
     * prima di tornare al chiamante; se la conferma non e' un OK solleva un'eccezione.
     */
    public synchronized void notifyAdd(String rilevazione) throws IOException {
        scrittore.println(Protocol.ADD + " " + rilevazione);
        String riga = leggiRisposta();
        if (!riga.startsWith(Protocol.OK)) {
            throw new IOException("notifica rifiutata dall'aggregatore: " + riga);
        }
    }

    /*
     * Chiede all'aggregatore l'elenco di tutte le rilevazioni disponibili sulla rete; ogni riga
     * restituita ha la forma "rilevazione peerId, peerId, ...".
     */
    public synchronized List<String> listRemote() throws IOException {
        return richiediElenco(Protocol.LIST);
    }

    /*
     * Chiede all'aggregatore l'elenco degli altri nodi sensore attivi; ogni riga restituita ha la
     * forma "peerId host porta".
     */
    public synchronized List<String> listNodes() throws IOException {
        return richiediElenco(Protocol.NODES);
    }

    /*
     * Chiede all'aggregatore quali nodi possiedono la rilevazione indicata; ogni riga restituita ha
     * la forma "peerId online|offline".
     */
    public synchronized List<String> whoHas(String rilevazione) throws IOException {
        return richiediElenco(Protocol.WHOHAS + " " + rilevazione);
    }

    /*
     * Comunica all'aggregatore che il nodo si sta disconnettendo e chiude il socket; viene
     * eseguito una sola volta anche se chiamato piu' volte. Questo metodo non e' synchronized e non
     * aspetta la risposta, perche' puo' essere chiamato anche dal thread di chiusura del programma
     * (Ctrl+C) mentre il thread della console e' fermo dentro un altro metodo di questa classe in
     * attesa di una risposta: se dovesse aspettare il lock, la chiusura del nodo resterebbe bloccata.
     * PrintWriter e' gia' sincronizzato al suo interno, quindi la riga DISCONNECT non si mescola con
     * altri messaggi. Chiudendo il socket, un eventuale thread in attesa di una risposta si sblocca
     * con un'eccezione.
     */
    public void disconnect() throws IOException {
        if (disconnesso) {
            return;
        }
        disconnesso = true;
        try {
            scrittore.println(Protocol.DISCONNECT);
        } finally {
            close();
        }
    }

    /*
     * Chiude il socket verso l'aggregatore, se non e' gia' chiuso.
     */
    @Override
    public void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    /*
     * I tre metodi seguenti sono usati dal Downloader per portare avanti una sessione di download:
     * requestDownload chiede all'aggregatore il token di accesso e il nodo che possiede la
     * rilevazione, retry segnala il fornitore che non ha funzionato per ottenerne un altro, done
     * conferma che il download e' andato a buon fine e rilascia il token. I primi due restituiscono
     * la riga di risposta dell'aggregatore cosi' come arriva, lasciando al Downloader il compito di
     * interpretarla; done solleva un'eccezione se l'aggregatore non risponde OK.
     */

    public synchronized String requestDownload(String rilevazione) throws IOException {
        scrittore.println(Protocol.DOWNLOAD + " " + rilevazione);
        return leggiRisposta();
    }

    public synchronized String retry(String token, String failedPeerId) throws IOException {
        scrittore.println(Protocol.RETRY + " " + token + " " + failedPeerId);
        return leggiRisposta();
    }

    public synchronized void done(String token, String fromPeerId, String rilevazione) throws IOException {
        scrittore.println(Protocol.DONE + " " + token + " " + fromPeerId + " " + rilevazione);
        String riga = leggiRisposta();
        if (!riga.startsWith(Protocol.OK)) {
            throw new IOException("conferma del download rifiutata dall'aggregatore: " + riga);
        }
    }
}
