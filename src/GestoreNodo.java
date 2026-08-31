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
 * Questa classe gestisce il dialogo dell'aggregatore con UN singolo nodo sensore:
 * ogni volta che un nodo si collega, ServerAggregatore crea un GestoreNodo su un
 * thread dedicato, cosi' l'aggregatore puo' servire piu' nodi contemporaneamente
 * senza che uno blocchi gli altri. La classe legge i messaggi che arrivano dal
 * nodo, li interpreta secondo il protocollo definito in Protocol.java, esegue
 * l'operazione richiesta sulla tabella condivisa e risponde al nodo.
 */
public class GestoreNodo implements Runnable {

    private final Socket socket;
    private final TabellaRilevazioni tabella;
    private final RegistroDownload registro;

    private String peerId;

    public GestoreNodo(Socket socket, TabellaRilevazioni tabella, RegistroDownload registro) {
        this.socket = socket;
        this.tabella = tabella;
        this.registro = registro;
    }

    // Ciclo di vita della connessione con il nodo: apre i flussi di lettura/scrittura sul socket e
    // resta in ascolto dei messaggi finche' il nodo non chiude la connessione o chiede
    // esplicitamente di disconnettersi. Ogni messaggio viene passato a gestioneMessaggio(), che si
    // occupa di interpretarlo e rispondere. Qualunque sia il motivo per cui il ciclo finisce
    // (disconnessione ordinata o caduta improvvisa), il nodo viene sempre marcato offline nel
    // blocco finally, cosi' non resta "fantasma" tra i nodi considerati attivi.
    @Override
    public void run() {
        try (
                socket;
                BufferedReader lettore = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter scrittore = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {

            String riga;
            while ((riga = lettore.readLine()) != null) {
                if (!gestioneMessaggio(riga, lettore, scrittore)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Il nodo si e' disconnesso in modo anomalo (es. crash): non c'e' altro da fare qui,
            // ci pensa comunque il blocco finally a marcarlo offline.
        } finally {
            if (peerId != null) {
                tabella.markOffline(peerId);
            }
        }
    }

    // Interpreta una singola riga ricevuta dal nodo, capisce quale comando rappresenta e lo
    // esegue chiamando i metodi giusti sulla tabella condivisa (e sul registro, per i download).
    // Restituisce false solo nel caso del comando DISCONNECT, che e' il segnale per uscire dal
    // ciclo di lettura in run(); in tutti gli altri casi restituisce true e la connessione resta
    // aperta per il messaggio successivo.
    private boolean gestioneMessaggio(String riga, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        String[] campi = riga.trim().split("\\s+");
        String comando = campi[0];

        switch (comando) {

            case Protocol.REGISTER -> {
                String host = campi[1];
                int port = Integer.parseInt(campi[2]);
                List<String> rilevazioni = (campi.length > 3 && !campi[3].equals("-"))
                        ? List.of(campi[3].split(",")) : List.of();
                peerId = tabella.registraNodo(host, port, rilevazioni);

                scrittore.println(Protocol.OK + " " + peerId);
            }

            case Protocol.ADD -> {
                tabella.addRilevazione (peerId, campi[1]);
                scrittore.println(Protocol.OK);
            }

            case Protocol.LIST -> {
                Map<String, List<String>> tutte = tabella.listaRilevazioni();
                for (Map.Entry<String, List<String>> e : tutte.entrySet()) {
                    scrittore.println(e.getKey() + " " + String.join(",", e.getValue()));
                }
                scrittore.println(Protocol.END);
            }

            case Protocol.NODES -> {
                scrittore.println(String.join(",", tabella.activeNodes()));
                scrittore.println(Protocol.END);
            }

            case Protocol.DOWNLOAD -> {
                gestisciSessioneDownload(campi[1], lettore, scrittore);
            }

            case Protocol.DISCONNECT -> {
                scrittore.println(Protocol.OK);
                return false;
            }
            default -> {
                scrittore.println(Protocol.ERR + " comando sconosciuto");
            }
        }
        return true;
    }

    // Gestisce dall'inizio alla fine una sessione di download per una rilevazione: cerca un primo
    // fornitore nella tabella e lo propone al richiedente, poi resta in attesa degli esiti che il
    // richiedente manda man mano che prova a scaricare davvero il file da quel fornitore. Se
    // arriva un RETRY (il fornitore proposto non ha funzionato) esclude quel nodo e ne cerca un
    // altro, ripetendo finche' non arriva un DONE (scaricato con successo) oppure finche' non
    // restano piu' fornitori da proporre (UNAVAILABLE). In ogni caso, l'esito finale della sessione
    // viene sempre registrato nel RegistroDownload.
    private void gestisciSessioneDownload(String rilevazione, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        String token = UUID.randomUUID().toString();

        Set<String> esclusi = new HashSet<>();

        InfoPeer fornitore = tabella.selectProvider(rilevazione, esclusi);
        if (fornitore == null) {
            registro.registra(rilevazione, "-", peerId, false);
            scrittore.println(Protocol.UNAVAILABLE);
            return;
        }
        scrittore.println(Protocol.PEER + " " + token + " " + fornitore.getPeerId() + " " + fornitore.getHost() + " " + fornitore.getPort());

        String riga;
        while ((riga = lettore.readLine()) != null) {
            String[] campi = riga.trim().split("\\s+");
            String comando = campi[0];

            switch (comando) {
                case Protocol.DONE -> {
                    tabella.addRilevazione(peerId, rilevazione);
                    registro.registra(rilevazione, campi[2], peerId, true);
                    scrittore.println(Protocol.OK);
                    return;
                }

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

                default -> {
                    scrittore.println(Protocol.ERR + " comando sconosciuto");
                    return;
                }
            }
        }
    }

}
