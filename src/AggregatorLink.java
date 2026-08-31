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

/**
 * Incapsula la connessione TCP persistente verso l'aggregatore centrale.
 * Sincronizza l'invio e la ricezione dei comandi per garantire la thread-safety 
 * ed evitare la sovrapposizione dei dati scambiati sul socket condiviso.
 */
public class AggregatorLink implements Closeable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private String peerId;

    /**
     * Inizializza il socket di rete verso l'aggregatore configurando i flussi I/O in UTF-8
     * e con auto-flush attivo per la trasmissione immediata dei messaggi.
     */
    public AggregatorLink(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        try {
            this.in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Restituisce l'identificativo univoco assegnato al nodo dall'aggregatore.
     */
    public synchronized String getPeerId() { 
        return peerId; 
    }

    /**
     * Recupera l'indirizzo IP locale utilizzato dalla scheda di rete per stabilire la connessione.
     */
    public String localAddress() { 
        return socket.getLocalAddress().getHostAddress(); 
    }

    /**
     * Effettua l'handshake iniziale inviando IP, porta del PeerServer locale e catalogo iniziale,
     * memorizzando l'ID univoco restituito dal server centrale.
     */
    public synchronized String register(String peerHost, int peerPort, List<String> rilevazioni) throws IOException {
        String ril = (rilevazioni == null || rilevazioni.isEmpty()) ? "-" : String.join(",", rilevazioni);
        out.println(Protocol.REGISTER + " " + peerHost + " " + peerPort + " " + ril);
        
        String reply = in.readLine();
        if (reply == null || !reply.startsWith(Protocol.OK)) {
            throw new IOException("Errore nella registrazione: " + reply);
        }

        String[] parts = reply.split("\\s+");
        if (parts.length < 2) {
            throw new IOException("Risposta dell'aggregatore non valida: " + reply);
        }
        
        this.peerId = parts[1];
        return this.peerId;
    }

    /**
     * Notifica all'aggregatore l'aggiunta di una nuova rilevazione per aggiornare l'indice di rete.
     */
    public synchronized void notifyAdd(String rilevazione) throws IOException {
        out.println(Protocol.ADD + " " + rilevazione);
        in.readLine();
    }

    /**
     * Richiede l'elenco completo delle risorse disponibili nella rete fino al segnale di terminazione.
     */
    public synchronized List<String> listRemote() throws IOException {
        out.println(Protocol.LIST);
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null && !line.equals(Protocol.END)) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * Invia la richiesta di disconnessione e rilascia in modo sicuro il socket di rete.
     */
    public synchronized void disconnect() throws IOException {
        try {
            out.println(Protocol.DISCONNECT);
            in.readLine();
        } finally {
            close();
        }
    }

    /**
     * Rilascia le risorse di rete chiudendo il socket sottostante se ancora aperto.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Avvia il flusso di download per una risorsa, ricevendo dall'aggregatore il nodo sorgente e il token.
     */
    public synchronized String requestDownload(String rilevazione) throws IOException {
        out.println(Protocol.DOWNLOAD + " " + rilevazione);
        return in.readLine();
    }

    /**
     * Segnala il fallimento di un peer sorgente e ottiene una posizione alternativa dall'aggregatore.
     */
    public synchronized String retry(String token, String failedPeerId) throws IOException {
        out.println(Protocol.RETRY + " " + token + " " + failedPeerId);
        return in.readLine();
    }

    /**
     * Conferma il completamento del download consentendo all'aggregatore di validare la sessione.
     */
    public synchronized void done(String token, String fromPeerId, String rilevazione) throws IOException {
        out.println(Protocol.DONE + " " + token + " " + fromPeerId + " " + rilevazione);
        in.readLine();
    }
}