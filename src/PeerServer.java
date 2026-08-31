import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * Questa classe e' il lato server P2P del nodo sensore: resta in ascolto su una
 * porta assegnata dal sistema operativo e accetta le richieste di download
 * provenienti dagli altri nodi. Gira su un thread di background, cosi' il nodo
 * puo' offrire le proprie rilevazioni agli altri mentre l'utente continua a
 * usare la console interattiva. Per ogni richiesta che arriva crea un
 * PeerRequestHandler su un thread dedicato, passandogli anche la FifoQueue
 * condivisa che garantisce che le richieste vengano servite una alla volta, in
 * ordine di arrivo.
 */
public class PeerServer implements Runnable {

    private final ServerSocket serverSocket;
    private final LocalStore store;
    private final FifoQueue serveLock = new FifoQueue();
    private volatile boolean running = true;

    // Crea il server aprendo subito un socket su una porta effimera assegnata dall'OS: la porta
    // effettiva si scopre poi con getPort() e va comunicata all'aggregatore in fase di
    // registrazione, cosi' gli altri nodi sanno dove contattare questo nodo.
    public PeerServer(LocalStore store) throws IOException {
        this.store = store;
        this.serverSocket = new ServerSocket(0);
    }

    // Restituisce la porta su cui il server sta ascoltando.
    public int getPort() { return serverSocket.getLocalPort(); }

    // Ciclo principale del server: finche' running e' true, accetta connessioni in arrivo e per
    // ognuna crea un PeerRequestHandler su un thread daemon dedicato, cosi' puo' subito tornare ad
    // accettare la richiesta successiva senza aspettare che quella corrente sia servita.
    @Override
    public void run() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread t = new Thread(new PeerRequestHandler(client, store, serveLock));
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) System.err.println("Errore PeerServer: " + e.getMessage());
            }
        }
    }

    // Ferma il server: imposta running a false e chiude il socket in ascolto, cosi' la accept()
    // bloccata in run() si sblocca ed esce dal ciclo.
    public void shutdown() {
        running = false;
        try { serverSocket.close(); } catch (IOException ignored) { }
    }
}
