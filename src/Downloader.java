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

    //prima di fare qualsiasi cosa in rete controlla se ha gia' la risorsa,
    // che in caso evita download inutili
    //richiedo il primo candidato
    public void download(String rilevazione) throws IOException { 
        if (archivio.has(rilevazione)) {
            System.out.println("Rilevazione gia' presente localmente.");
            return;
        }
        String reply = aggregatore.requestDownload(rilevazione);
// ad ogni iterazione controllo se la risposta e'negativa e nel caso esco, perche' questa e' l'unica via d'uscita negativa del ciclo
//se non e' negativa spezzo la riga su piu' spazi (split): t[0] e' la parola peer (non usata), t[1] e' token, ecc.
//!!!questo presuppone che il formato sia sempre esatto, qualora l'aggregatore mandasse un input errato, soppierebbe un eccezione che non viene gestista!!!
        while (true) { 
            if (reply.startsWith(Protocol.UNAVAILABLE)) {
                System.out.println("Rilevazione '" + rilevazione + "' non disponibile sulla rete.");
                return;
            }
            String[] t = reply.split("\\s+");
            String token = t[1], providerId = t[2], host = t[3];
            int port = Integer.parseInt(t[4]);
//si passa dall'aggregatore al peer-to-peer: mi collego all'host/porta che ho ricevuto, senza passare piu' dall'aggregatore
            String contenuto = fetchFromPeer(host, port, rilevazione);

//CASO POSITIVO
//se il contenuto ha restituito qualcosa, lo salva nell'archivio e comunica all'aggregatore un done passandogli token, providerId,
// così l'aggregatore sa quale sessione chiudere e quale nodo ha effettivamente servito il file, stampa conferma ed esce: unica via d'uscita positiva
            if (contenuto != null) {
                store.add(rilevazione, contenuto);
                aggregatore.done(token, providerId, rilevazione);
                System.out.println("Rilevazione '" + rilevazione + "' scaricata da " + providerId + ".");
                return;             
            } else {
    // Il nodo non possiede piu' la rilevazione, ma non si arrende: richiama l'aggregatore con retry passandogli token,
    // e providerId(nodo che ha fallito così l'aggregatore ne propone un altro).. La risposta di retry ha la stessa forma di requestDownload e il ciclo while ricomeincia da capo.
                reply = aggregatore.retry(token, providerId);
            }
        }
    }

/** Contatta il PeerServer di un altro nodo. Ritorna il contenuto o null (NOTFOUND / errore). */

    private String fetchFromPeer(String host, int port, String rilevazione) {
        try (Socket s = new Socket(host, port);
             BufferedReader lettore = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter scrittore = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
//Apre un socket TCP diretto verso il peer candidato (niente a che fare con l'aggregatore). 
//Usa try-with-resources: socket e stream si chiudono automaticamente, anche in caso di eccezione.

            out.println(Protocol.GET + " " + rilevazione); //Invia il comando GET <rilevazione> e legge una riga di risposta
            String resp = in.readLine();
//Se la risposta inizia con OK, il resto della riga è il contenuto codificato in Base64.
// Lo decodifica e lo restituisce come stringa UTF-8.
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
