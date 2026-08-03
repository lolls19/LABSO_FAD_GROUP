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
 * Gestisce l'unica connessione persistente del nodo sensore verso l'aggregatore.
 * Tutti i comandi che dialogano con l'aggregatore passano da qui.
 *
 * I metodi sono "synchronized": la connessione e' condivisa (console e
 * Downloader), quindi una richiesta/risposta per volta viaggia sul socket,
 * evitando interleaving corrotti.
 */
public class AggregatorLink implements Closeable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private String peerId;

    // Crea il socket e inizializza i flussi di testo in UTF-8
    public AggregatorLink(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        try {
            this.in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // true attiva l'autoflush: ogni riga inviata parte subito
            this.out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            // Se qualcosa fallisce all'avvio, chiudo il socket per non lasciare porte aperte
            socket.close();
            throw e;
        }
    }

    public synchronized String getPeerId() { 
        return peerId; 
    }

    // Restituisce l'IP locale usato per farsi raggiungere dagli altri peer
    public String localAddress() { 
        return socket.getLocalAddress().getHostAddress(); 
    }

    // Registra il nodo sull'aggregatore e salva l'ID assegnato
    public synchronized String register(String peerHost, int peerPort, List<String> rilevazioni) throws IOException {
        // Se non ci sono risorse locali usiamo "-" come segnaposto
        String ril = (rilevazioni == null || rilevazioni.isEmpty()) ? "-" : String.join(",", rilevazioni);
        out.println(Protocol.REGISTER + " " + peerHost + " " + peerPort + " " + ril);
        
        String reply = in.readLine();
        // Controllo che la risposta sia valida prima di fare lo split
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

    // Avvisa l'aggregatore che abbiamo aggiunto una nuova rilevazione
    public synchronized void notifyAdd(String rilevazione) throws IOException {
        out.println(Protocol.ADD + " " + rilevazione);
        in.readLine(); // Aspetta la conferma OK
    }

    // Chiede la lista delle rilevazioni disponibili sulla rete
    public synchronized List<String> listRemote() throws IOException {
        out.println(Protocol.LIST);
        List<String> lines = new ArrayList<>();
        String line;
        // Legge le righe finché l'aggregatore non invia il segnale END
        while ((line = in.readLine()) != null && !line.equals(Protocol.END)) {
            lines.add(line);
        }
        return lines;
    }

    // Saluta l'aggregatore e chiude la connessione
    public synchronized void disconnect() throws IOException {
        try {
            out.println(Protocol.DISCONNECT);
            in.readLine(); // Aspetta l'OK finale
        } finally {
            close(); // Chiude il socket anche se il comando sopra fallisce
        }
    }

    // Rilascia le risorse della rete
    @Override
    public synchronized void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    // Metodi usati dal Downloader durante la ricerca e scaricamento

    // Chiede all'aggregatore chi possiede la risorsa e il token di sessione
    public synchronized String requestDownload(String rilevazione) throws IOException {
        out.println(Protocol.DOWNLOAD + " " + rilevazione);
        return in.readLine();
    }

    // Segnala che il nodo indicato ha fallito e chiede un'alternativa
    public synchronized String retry(String token, String failedPeerId) throws IOException {
        out.println(Protocol.RETRY + " " + token + " " + failedPeerId);
        return in.readLine();
    }

    // Notifica che il download è andato a buon fine per chiudere la sessione
    public synchronized void done(String token, String fromPeerId, String rilevazione) throws IOException {
        out.println(Protocol.DONE + " " + token + " " + fromPeerId + " " + rilevazione);
        in.readLine();
    }
}