import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * Questa classe implementa il comando "download <rilevazione>" lato nodo
 * richiedente, seguendo il protocollo di download robusto: prima chiede
 * all'aggregatore un token e il nodo che dovrebbe possedere la rilevazione, poi
 * si collega direttamente a quel nodo per scaricarla via peer-to-peer. Se il
 * nodo scelto non ce l'ha piu' (risposta NOTFOUND), lo comunica all'aggregatore
 * con un RETRY e ne ottiene un altro da provare, e cosi' via finche' non riesce a
 * scaricare la rilevazione (DONE) oppure l'aggregatore esaurisce i nodi da
 * proporre (UNAVAILABLE, cioe' non disponibile sulla rete).
 */
public class Downloader {

    private final AggregatorLink aggregatore;
    private final LocalStore store;

    public Downloader(AggregatorLink aggregatore, LocalStore store) {
        this.aggregatore = aggregatore;
        this.store = store;
    }

    // Scarica una rilevazione dalla rete: se il nodo la possiede gia' localmente non fa nulla e lo
    // segnala. Altrimenti chiede all'aggregatore un primo candidato e prova a contattarlo
    // direttamente; se il candidato non ha piu' la rilevazione, avvisa l'aggregatore con un RETRY
    // e riprova con il nodo successivo che viene proposto, ripetendo il ciclo finche' non riesce a
    // scaricarla (caso in cui salva il contenuto, notifica il DONE all'aggregatore ed esce) oppure
    // finche' l'aggregatore risponde che la rilevazione non e' disponibile su nessun nodo.
    public void download(String rilevazione) throws IOException {
        if (store.has(rilevazione)) {
            System.out.println("Rilevazione gia' presente localmente.");
            return;
        }

        String riga = aggregatore.requestDownload(rilevazione);

        while (true) {
            if (riga.startsWith(Protocol.UNAVAILABLE)) {
                System.out.println("Rilevazione '" + rilevazione + "' non disponibile sulla rete.");
                return;
            }

            String[] campi = riga.split("\\s+");
            String token = campi[1], providerId = campi[2], host = campi[3];
            int port = Integer.parseInt(campi[4]);

            String contenuto = fetchFromPeer(host, port, rilevazione);

            if (contenuto != null) {
                store.add(rilevazione, contenuto);
                aggregatore.done(token, providerId, rilevazione);
                System.out.println("Rilevazione '" + rilevazione + "' scaricata da " + providerId + ".");
                return;
            } else {
                riga = aggregatore.retry(token, providerId);
            }
        }
    }

    // Si collega direttamente al nodo indicato (senza passare dall'aggregatore) e gli chiede la
    // rilevazione con un GET. Se il nodo la possiede risponde OK seguito dal contenuto codificato
    // in Base64, che qui viene decodificato e restituito. Se il nodo non la possiede piu', oppure
    // se non e' proprio raggiungibile (offline, connessione rifiutata...), il metodo restituisce
    // null in entrambi i casi: dal punto di vista di chi chiama non fa differenza, serve comunque
    // solo chiedere un altro candidato all'aggregatore, senza far fallire tutto il download.
    private String fetchFromPeer(String host, int port, String rilevazione) {
        try (Socket s = new Socket(host, port);
             BufferedReader lettore = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter scrittore = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            scrittore.println(Protocol.GET + " " + rilevazione);
            String resp = lettore.readLine();

            if (resp != null && resp.startsWith(Protocol.OK)) {
                String encoded = resp.substring(Protocol.OK.length()).trim();
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            }
            return null;

        } catch (IOException e) {
            return null;
        }
    }
}
