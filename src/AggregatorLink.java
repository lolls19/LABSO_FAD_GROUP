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
 * nuove rilevazioni, richiesta dell'elenco delle risorse di rete, e tutti i
 * messaggi legati al download (richiesta, retry, conferma finale). Sia la
 * console interattiva sia il Downloader usano questo stesso oggetto per parlare
 * con l'aggregatore, quindi tutti i metodi sono synchronized: cosi' una
 * richiesta e la sua risposta viaggiano sempre una alla volta sullo stesso
 * socket, senza che i messaggi di due chiamate diverse si mescolino.
 */
public class AggregatorLink implements Closeable {

    private final Socket socket;
    private final BufferedReader lettore;
    private final PrintWriter scrittore;
    private String peerId;

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
     * Registra questo nodo presso l'aggregatore, comunicando il proprio indirizzo, la porta P2P e
     * le rilevazioni possedute all'avvio (oppure "-" se non ne ha nessuna). Controlla che la
     * risposta sia effettivamente un OK con un id valido prima di accettarla, altrimenti solleva
     * un'eccezione; se tutto va bene salva l'id assegnato e lo restituisce.
     */
    public synchronized String register(String peerHost, int peerPort, List<String> rilevazioni) throws IOException {
        String ril = (rilevazioni == null || rilevazioni.isEmpty()) ? "-" : String.join(",", rilevazioni);
        scrittore.println(Protocol.REGISTER + " " + peerHost + " " + peerPort + " " + ril);

        String riga = lettore.readLine();
        if (riga == null || !riga.startsWith(Protocol.OK)) {
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
     * prima di tornare al chiamante.
     */
    public synchronized void notifyAdd(String rilevazione) throws IOException {
        scrittore.println(Protocol.ADD + " " + rilevazione);
        lettore.readLine();
    }

    /*
     * Chiede all'aggregatore l'elenco di tutte le rilevazioni disponibili sulla rete e restituisce
     * le righe ricevute cosi' come sono (una per rilevazione), leggendo finche' non arriva il
     * separatore di fine elenco.
     */
    public synchronized List<String> listRemote() throws IOException {
        scrittore.println(Protocol.LIST);
        List<String> righe = new ArrayList<>();
        String riga;
        while ((riga = lettore.readLine()) != null && !riga.equals(Protocol.END)) {
            righe.add(riga);
        }
        return righe;
    }

    /*
     * Comunica all'aggregatore che il nodo si sta disconnettendo in modo ordinato e aspetta la
     * conferma; qualunque cosa succeda durante lo scambio, chiude comunque il socket alla fine.
     */
    public synchronized void disconnect() throws IOException {
        try {
            scrittore.println(Protocol.DISCONNECT);
            lettore.readLine();
        } finally {
            close();
        }
    }

    /*
     * Chiude il socket verso l'aggregatore, se non e' gia' chiuso.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    /*
     * I tre metodi seguenti sono usati dal Downloader per portare avanti una sessione di download:
     * chiedono all'aggregatore chi possiede la rilevazione, segnalano un fornitore che non ha
     * funzionato per ottenerne un altro, e infine confermano che il download e' andato a buon
     * fine. In tutti e tre i casi restituiscono semplicemente la riga di risposta dell'aggregatore
     * cosi' come arriva, lasciando al Downloader il compito di interpretarla.
     */

    public synchronized String requestDownload(String rilevazione) throws IOException {
        scrittore.println(Protocol.DOWNLOAD + " " + rilevazione);
        return lettore.readLine();
    }

    public synchronized String retry(String token, String failedPeerId) throws IOException {
        scrittore.println(Protocol.RETRY + " " + token + " " + failedPeerId);
        return lettore.readLine();
    }

    public synchronized void done(String token, String fromPeerId, String rilevazione) throws IOException {
        scrittore.println(Protocol.DONE + " " + token + " " + fromPeerId + " " + rilevazione);
        lettore.readLine();
    }
}
