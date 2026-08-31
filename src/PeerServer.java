import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Lato "server" del nodo sensore: resta in ascolto e accetta le richieste di
 * download provenienti dagli altri nodi. Gira su un thread di background, in
 * modo che il nodo possa contemporaneamente offrire le proprie rilevazioni e
 * usare la console interattiva.
 *
 * La porta e' assegnata dal sistema operativo (ServerSocket(0)) e comunicata
 * all'aggregatore in fase di registrazione.
 *
 * Espone il "serveLock" condiviso che realizza la regola "una richiesta per
 * volta" (vedi PeerRequestHandler): e' una FifoQueue, quindi gli handler sono
 * serviti nell'ordine di arrivo, e ognuno la acquisisce prima di servire la
 * richiesta e la rilascia al termine.
 *

 */
public class PeerServer implements Runnable {

    private final ServerSocket serverSocket;
    private final LocalStore store;
    private final FifoQueue serveLock = new FifoQueue();
    private volatile boolean running = true;

    public PeerServer(LocalStore store) throws IOException {
        this.store = store;
        this.serverSocket = new ServerSocket(0);
    }

    public int getPort() { return serverSocket.getLocalPort(); }

    @Override
    public void run() {
        while (running) {
            try {
                Socket peer = serverSocket.accept();
                Thread t = new Thread(new PeerRequestHandler(peer, store, serveLock));
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) System.err.println("Errore PeerServer: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        running = false;
        try { serverSocket.close(); } catch (IOException ignored) { }
    }
}