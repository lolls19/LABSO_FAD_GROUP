import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * Questa classe e' il "portiere" dell'aggregatore: resta in ascolto sulla porta
 * indicata e, ogni volta che un nodo sensore si collega, crea un GestoreNodo
 * dedicato su un thread separato per occuparsi di quella connessione. In questo
 * modo l'aggregatore puo' accettare e servire piu' nodi in parallelo, invece di
 * doversi occupare di uno alla volta. Gira su un proprio thread di background,
 * cosi' il thread principale resta libero di gestire la console interattiva.
 */
public class ServerAggregatore implements Runnable {

    private final int port;
    private final TabellaRilevazioni tabella;
    private final RegistroDownload registro;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public ServerAggregatore(int port, TabellaRilevazioni tabella, RegistroDownload registro) {
        this.port = port;
        this.tabella = tabella;
        this.registro = registro;
    }

    // Apre il socket in ascolto sulla porta indicata e resta in un ciclo ad accettare nuove
    // connessioni finche' running resta true: per ogni nodo che si collega crea un GestoreNodo e
    // lo avvia su un thread daemon dedicato, cosi' puo' tornare subito ad accettare la connessione
    // successiva senza aspettare che quella corrente finisca. Se il socket viene chiuso da
    // shutdown() mentre il server e' ancora "running", l'eccezione che ne deriva viene ignorata
    // perche' e' l'effetto voluto della chiusura volontaria; altrimenti viene stampato un errore.
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Aggregatore in ascolto sulla porta " + port);

            while (running) {
                Socket client = serverSocket.accept();

                Thread t = new Thread(new GestoreNodo(client, tabella, registro));
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (running) System.err.println("Errore server: " + e.getMessage());
        }
    }

    // Ferma il server: imposta running a false e chiude il socket in ascolto, cosi' la accept()
    // bloccata in run() si sblocca subito con un'eccezione (che verra' ignorata proprio perche'
    // running e' ormai false) e il ciclo termina. Viene chiamato dalla console quando si digita
    // "quit".
    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
    }
}
