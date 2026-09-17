import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
Questa classe resta in ascolto sulla porta
indicata e, ogni volta che un nodo sensore si collega, crea un GestoreNodo
dedicato su un thread separato per occuparsi di quella connessione. In questo
modo l'aggregatore puo' accettare e servire piu' nodi in parallelo, invece di
doversi occupare di uno alla volta. Gira su un proprio thread di background,
cosi' il thread principale resta libero di gestire la console interattiva.
*/
public class ServerAggregatore implements Runnable {

    private final TabellaRilevazioni tabella;
    private final RegistroDownload registro;
    private volatile boolean running = true;
    private final ServerSocket serverSocket;

    /*
    metodo costruttore che inizializza la tabella delle rilevazioni e il registro dei download e apre
    subito il socket in ascolto sulla porta indicata. Aprirlo qui, e non nel thread di background,
    permette al Master di accorgersi immediatamente se la porta e' gia' occupata (o non valida) e
    di terminare con un messaggio di errore, invece di restare con una console attiva ma senza
    nessun server realmente in ascolto.
    */
    public ServerAggregatore(int port, TabellaRilevazioni tabella, RegistroDownload registro) throws IOException {
        this.tabella = tabella;
        this.registro = registro;
        this.serverSocket = new ServerSocket(port);
    }

    /* Restituisce la porta su cui il server e' in ascolto. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /*
    Resta in un ciclo ad accettare nuove connessioni finche' running resta true: per ogni nodo
    che si collega crea un GestoreNodo e lo avvia su un thread daemon dedicato, cosi' puo' tornare
    subito ad accettare la connessione successiva senza aspettare che quella corrente finisca.
    Se il socket viene chiuso da shutdown(), l'eccezione che ne deriva viene ignorata perche' e'
    l'effetto voluto della chiusura volontaria; altrimenti viene stampato un errore.
    */
    @Override
    public void run() {
        try {
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

    /*
    metodo che ferma il server chiudendo il socket: se il thread e' bloccato in accept() si sblocca e termina.
    questo metodo viene chiamato dal Master quando l'utente digita "quit" sulla console interattiva.
    */
    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) { }
    }
}
