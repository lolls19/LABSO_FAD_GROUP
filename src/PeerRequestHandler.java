
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Serve UNA richiesta di download proveniente da un altro nodo sensore.
 *
 * MECCANISMO DI SINCRONIZZAZIONE #2 (lato nodo sensore):
 * il nodo serve una richiesta per volta. Tutti i PeerRequestHandler del nodo
 * condividono la stessa FifoQueue "serveLock": prima di servire la richiesta
 * l'handler chiama acquisisciLock() e al termine rilascioLock(), cosi' ogni
 * altra richiesta indirizzata a questo nodo resta in attesa fino al
 * completamento, senza terminare con errore. Essendo una coda FIFO, le
 * richieste vengono servite nell'ordine in cui sono arrivate.
 *

 */
public class PeerRequestHandler implements Runnable {

    private final Socket socket;
    private final LocalStore store;
    private final FifoQueue serveLock;

    public PeerRequestHandler(Socket socket, LocalStore store, FifoQueue serveLock) {
        this.socket = socket;
        this.store = store;
        this.serveLock = serveLock;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line = in.readLine();
            if (line == null) return;
            String[] t = line.trim().split("\\s+");

            if (t[0].equals(Protocol.GET)) {
                String rilevazione = t[1];
                // Una richiesta per volta: le altre attendono qui il proprio turno.
                serveLock.acquisisciLock();
                try {
                    if (store.has(rilevazione)) {
                        String encoded = Base64.getEncoder().encodeToString(
                                store.get(rilevazione).getBytes(StandardCharsets.UTF_8));
                        out.println(Protocol.OK + " " + encoded);
                    } else {
                        // La rilevazione non c'e' piu': innesca il protocollo robusto lato richiedente.
                        out.println(Protocol.NOTFOUND);
                    }
                } finally {
                    // Il rilascio deve avvenire comunque, altrimenti la coda si blocca
                    // per tutte le richieste successive.
                    serveLock.rilascioLock();
                }
            } else {
                out.println(Protocol.ERR + " comando peer sconosciuto");
            }
        } catch (IOException e) {
            // Interruzione anomala della connessione peer: si ignora, il richiedente ritentera'.
        }
    }
}
