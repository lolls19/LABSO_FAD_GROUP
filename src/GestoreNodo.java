
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/*
permette di il dialogo con un nodo sensore. Ogni nodo che si connette al server viene
 gestito da un GestoreNodo su un thread dedicato.
 questo permette al server di servire più nodi contemporaneamente senza bloccare l'ascolto di nuovi nodi.
 Questo in base al messaggio interprettao tramite il protocollo definito in Protocol.java,
  esegue le azioni richieste e risponde al nodo.

 */
public class GestoreNodo implements Runnable {

    private final Socket socket;              // connessione col nodo
    private final TabellaRilevazioni tabella; // tabella condivisa
    private final RegistroDownload registro;  // registro dei download, da usare poi quando si implementarà la gesetione del download

    private String peerId;               // identificativo assegnato al nodo dopo il REGISTER

    public GestoreNodo(Socket socket, TabellaRilevazioni tabella, RegistroDownload registro) {
        this.socket = socket;
        this.tabella = tabella;
        this.registro = registro;
    }
    /* il metodo run implementa la logica di connessione con il nodo
        legge i messaggi in arrivo dal nodo finche' il nodo 
     non chiude o non chiede la disconnessione.
     Pe rla gestione dei messaggi si richiama il metodo gestioneMessaggio che interpreta il messaggio e agisce di conseguenza.
     */
    
    @Override
    public void run() {
        // si usa try-with-resources per chiudere automaticamente il socket e i flussi di input/output alla fine del blocco,
        // il bufferedReader legge i messaggi in arrivo dal nodo, il PrintWriter invia le risposte al nodo.
        try (
                socket;
                BufferedReader lettore = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); 
                PrintWriter scrittore = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {

            String riga;
            // Legge i messaggi finche' il nodo non chiude o non chiede la disconnessione.
            while ((riga = lettore.readLine()) != null) {
                if (!gestioneMessaggio(riga, lettore, scrittore)) {
                    break; // false = il nodo ha chiesto DISCONNECT

                }
            }
        } catch (IOException e) {
            // se il nodo si disconnette in modo imprevisto (caduta), si gestisce comunque con il finally
            //  per marcare il nodo offline
        } finally {
            // In ogni caso (disconnessione ordinata o caduta), se il nodo era registrato
            // lo si marca offline, non verra' piu' proposto ne' elencato.
            if (peerId != null) {
                tabella.markOffline(peerId);
            }
        }
    }

    /*
      Interpreta un singolo messaggio e agisce di conseguenza. Ritorna false
      solo se il nodo ha chiesto di disconnettersi (per uscire dal ciclo).
     */
    private boolean gestioneMessaggio(String riga, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        String[] campi = riga.trim().split("\\s+"); // spezza la riga in comando + argomenti
        String comando = campi[0];

        switch (comando) {

            // REGISTER <host> <porta> <ril1,ril2,...|->  -> registra il nodo e restituisce l'id
            case Protocol.REGISTER -> {
                String host = campi[1];
                int port = Integer.parseInt(campi[2]);
                // Se il quarto campo esiste e non e' il segnaposto "-", sono le rilevazioni iniziali.
                List<String> rilevazioni = (campi.length > 3 && !campi[3].equals("-"))
                        ? List.of(campi[3].split(",")) : List.of();
                peerId = tabella.registraNodo(host, port, rilevazioni); // assegna l'id (peer0, peer1, ...)

                scrittore.println(Protocol.OK + " " + peerId);      // risponde con l'id assegnato
            }

            // ADD <rilevazione>  -> il nodo ha una nuova rilevazione
            case Protocol.ADD -> {
                tabella.addRilevazione (peerId, campi[1]);
                scrittore.println(Protocol.OK);
            }

            // LIST  -> elenco rilevazioni remote (una riga per rilevazione), poi END per segnalare la fine dell'elenco
            case Protocol.LIST -> {
                Map<String, List<String>> tutte = tabella.listaRilevazioni();
                for (Map.Entry<String, List<String>> e : tutte.entrySet()) {
                    // formato: "R0 peer0,peer3"
                    scrittore.println(e.getKey() + " " + String.join(",", e.getValue()));
                }
                scrittore.println(Protocol.END); // segnala al nodo che l'elenco e' finito
            }

            // NODES  -> elenco dei nodi attivi
            case Protocol.NODES -> {
                scrittore.println(String.join(",", tabella.activeNodes()));
                scrittore.println(Protocol.END);
            }

            // DOWNLOAD <rilevazione>  -> il nodo cerca un fornitore da cui scaricare la rilevazione
            case Protocol.DOWNLOAD -> {
                gestisciSessioneDownload(campi[1], lettore, scrittore);
            }

            // DISCONNECT  -> il nodo si disconnette in modo ordinato
            case Protocol.DISCONNECT -> {
                scrittore.println(Protocol.OK);
                return false; // esce dal ciclo di lettura
            }
            // di default, se il comando non e' riconosciuto, risponde con un errore
            default -> {
                scrittore.println(Protocol.ERR + " comando sconosciuto");
            }
        }
        return true;
    }

    /*
      Gestisce l'intera sessione di download di una rilevazione: cerca un fornitore,
      lo propone al richiedente e resta in ascolto finche' non arriva un esito (DONE)
      o il richiedente chiede un altro fornitore (RETRY).
     */
    private void gestisciSessioneDownload(String rilevazione, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        // token che identifica questa sessione, cosi' i messaggi successivi si riferiscono ad essa
        String token = UUID.randomUUID().toString();

        // nodi gia' proposti e scartati, per non riproporli
        Set<String> esclusi = new HashSet<>();

        // cerca un primo fornitore per la rilevazione richiesta
        InfoPeer fornitore = tabella.selectProvider(rilevazione, esclusi);
        if (fornitore == null) {
            registro.registra(rilevazione, "-", peerId, false);
            scrittore.println(Protocol.UNAVAILABLE);
            return;
        }
        scrittore.println(Protocol.PEER + " " + token + " " + fornitore.getPeerId() + " " + fornitore.getHost() + " " + fornitore.getPort());

        // attende gli esiti del download diretto tra richiedente e fornitore
        String riga;
        while ((riga = lettore.readLine()) != null) {
            String[] campi = riga.trim().split("\\s+");
            String comando = campi[0];

            switch (comando) {
                // il richiedente ha scaricato con successo dal fornitore
                case Protocol.DONE -> {
                    tabella.addRilevazione(peerId, rilevazione);
                    registro.registra(rilevazione, campi[2], peerId, true);
                    scrittore.println(Protocol.OK);
                    return;
                }

                // il fornitore proposto non ha risposto, si esclude e se ne cerca un altro
                case Protocol.RETRY -> {
                    String fallito = campi[2];
                    esclusi.add(fallito);
                    tabella.removeEntryRilevazione(fallito, rilevazione);

                    fornitore = tabella.selectProvider(rilevazione, esclusi);
                    if (fornitore == null) {
                        registro.registra(rilevazione, "-", peerId, false);
                        scrittore.println(Protocol.UNAVAILABLE);
                        return;
                    }
                    scrittore.println(Protocol.PEER + " " + token + " " + fornitore.getPeerId() + " " + fornitore.getHost() + " " + fornitore.getPort());
                }

                // qualsiasi altro messaggio non e' atteso in questa fase
                default -> {
                    scrittore.println(Protocol.ERR + " comando sconosciuto");
                    return;
                }
            }
        }
    }

}
