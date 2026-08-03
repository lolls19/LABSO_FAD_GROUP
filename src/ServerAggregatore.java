import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
  Ciclo di accettazione delle connessioni dei nodi sensore.
  Per ogni nodo che si collega, avvia un GestoreNodo su un thread dedicato,
  cosi' che l'aggregatore possa servire piu' nodi in parallelo.
 Gira su un proprio thread di background per non bloccare la console.
 */
public class ServerAggregatore implements Runnable {

    private final int port;                    // porta su cui l'aggregatore ascolta
    private final TabellaRilevazioni tabella;  // tabella condivisa
    private final RegistroDownload registro;   // registro dei download condiviso
    private volatile boolean running = true;   // flag per il ciclo di ascolto: true=continua, false=termina
    private ServerSocket serverSocket;         // il socket in ascolto (creato in run)

    public ServerAggregatore(int port, TabellaRilevazioni tabella, RegistroDownload registro) {
        this.port = port;
        this.tabella = tabella;
        this.registro = registro;
    }

    @Override
    public void run() {
        try {
            // Apre il socket in ascolto sulla porta indicata.
            // Da qui in poi l'aggregatore puo' ricevere connessioni dai nodi.
            serverSocket = new ServerSocket(port);
            System.out.println("Aggregatore in ascolto sulla porta " + port);

            // Ciclo principale: resta in ascolto finchè running è true.
            while (running) {
                // accept() è bloccante, il thread si ferma qui finche' un nodo
                // non si connette. Quando arriva una connessione, restituisce il
                // socket per dialogare con quel nodo.
                Socket client = serverSocket.accept();

                // Per ogni nodo crea un GestoreNodo e lo avvia su un thread
                // dedicato, così l'aggregatore serve più nodi contemporaneamente
                // e può tornare subito ad accettare la connessione successiva
                Thread t = new Thread(new GestoreNodo(client, tabella, registro));
                t.setDaemon(true);  // thread subordinato: non impedisce la chiusura del programma
                t.start();
            }
        } catch (IOException e) {
            // Se running è false, significa che il server è stato chiuso volontariamente
            //con lo shutdown() della console. In tal caso, non stampare l'errore.
            //altrimenti, se running è true, significa che c'è stato un errore imprevisto e
            // lo stampa
            if (running) System.err.println("Errore server: " + e.getMessage());
        }
    }

    //chiude il server di ascolto e termina il ciclo di run(), grazie al flag running=false. 
    // Il thread di run() termina e il server si chiude.
    // si richiama dalla console quando si digita "quit"
    public void shutdown() {
        running = false;               
        try {
            //  chiude il socket in ascolto. Questo sblocca la accept() bloccata,
            //    lancia una IOException che, essendo running=false, viene ignorata.
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
    }
}