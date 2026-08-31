import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * Questa classe serve UNA singola richiesta di download proveniente da un altro
 * nodo sensore: legge il comando GET <rilevazione>, controlla se il nodo
 * possiede davvero quella rilevazione e risponde di conseguenza (il contenuto
 * codificato in Base64 se ce l'ha, oppure NOTFOUND se non ce l'ha piu'). Prima
 * di accedere all'archivio acquisisce la FifoQueue condivisa con tutti gli altri
 * PeerRequestHandler dello stesso nodo, cosi' le richieste vengono servite una
 * alla volta e nell'ordine in cui sono arrivate, invece di poter interferire tra
 * loro.
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

    // Legge la richiesta in arrivo sul socket: se e' un GET, si mette in coda sulla FifoQueue,
    // controlla se il nodo possiede la rilevazione richiesta e risponde con il contenuto codificato
    // in Base64 oppure con NOTFOUND, rilasciando sempre il lock alla fine (anche in caso di
    // errore, grazie al finally). Se il comando ricevuto non e' un GET risponde con un errore.
    // Qualunque problema di connessione viene semplicemente ignorato: il nodo richiedente, dal suo
    // punto di vista, vedra' la richiesta fallita e riprovera' con un altro nodo.
    @Override
    public void run() {
        try (socket;
             BufferedReader lettore = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter scrittore = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String riga = lettore.readLine();
            if (riga == null) return;
            String[] campi = riga.trim().split("\\s+");

            if (campi[0].equals(Protocol.GET)) {
                String rilevazione = campi[1];
                serveLock.acquisisciLock();
                try {
                    if (store.has(rilevazione)) {
                        String encoded = Base64.getEncoder().encodeToString(
                                store.get(rilevazione).getBytes(StandardCharsets.UTF_8));
                        scrittore.println(Protocol.OK + " " + encoded);
                    } else {
                        scrittore.println(Protocol.NOTFOUND);
                    }
                } finally {
                    serveLock.rilascioLock();
                }
            } else {
                scrittore.println(Protocol.ERR + " comando peer sconosciuto");
            }
        } catch (IOException e) {
            // Connessione caduta in modo anomalo: non c'e' nulla da fare, il richiedente riprovera'.
        }
    }
}
