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
Questa classe gestisce il dialogo dell'aggregatore con un singolo nodo sensore:
ogni volta che un nodo si collega tramite il socket, ServerAggregatore crea un GestoreNodo su un
thread dedicato, cosi' l'aggregatore puo' servire piu' nodi contemporaneamente
senza che uno blocchi gli altri. La classe legge i messaggi che arrivano dal
nodo, li interpreta secondo il protocollo definito in Protocol.java, esegue
l'operazione richiesta sulla tabella condivisa e risponde al nodo.
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

    /*
    Il metodo run() apre i flussi di lettura/scrittura sul socket e
    resta in ascolto dei messaggi finche' il nodo non chiude la connessione o chiede
    esplicitamente di disconnettersi. Ogni messaggio viene passato a gestioneMessaggio(), che si
    occupa di interpretarlo e rispondere. Qualunque sia il motivo per cui il ciclo finisce
    (disconnessione voluta o caduta improvvisa), il nodo viene sempre marcato offline nel
    blocco finally, cosi' non resta "fantasma" tra i nodi considerati attivi.
    */
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
            /*
            Il nodo si e' disconnesso in modo anomalo (es. crash): non c'e' altro da fare qui,
            ci pensa comunque il blocco finally a marcarlo offline.
            */
        } finally {
            if (peerId != null) {
                tabella.markOffline(peerId);
            }
        }
    }

    /*
    Interpreta una singola riga ricevuta dal nodo, capisce quale comando rappresenta e lo
    esegue chiamando i metodi di TabellaRilevazioni e su RegistroDownload.
    Restituisce false quando la connessione con il nodo deve terminare (DISCONNECT, oppure
    connessione caduta durante una sessione di download), che e' il segnale per uscire dal
    ciclo di lettura in run(); in tutti gli altri casi restituisce true e la connessione resta
    aperta per il messaggio successivo.
    i tipi di comandi gestiti sono:
    - REGISTER: il nodo si registra per la prima volta, viene creato un nuovo InfoPeer
    e gli viene assegnato un id progressivo, e se ci sono rilevazioni da registrare le registra subito.
    - ADD: il nodo annuncia di avere una nuova rilevazione, che viene aggiunta alla sua entry in tabella.
    - LIST: il nodo chiede la lista di tutte le rilevazioni possedute da tutti i nodi, che viene restituita
    come una serie di righe "rilevazione peerId, peerId, ..." seguite da un END.
    - NODES: il nodo chiede l'elenco degli altri nodi sensore attivi, restituiti come righe
    "peerId host porta" seguite da un END (il nodo richiedente non compare nell'elenco).
    - WHOHAS: il nodo chiede quali nodi possiedono una determinata rilevazione, restituiti come righe
    "peerId online|offline" seguite da un END.
    - DOWNLOAD: il nodo chiede di scaricare una rilevazione, e si apre una sessione di download.
    - DISCONNECT: il nodo chiede di disconnettersi, la connessione viene chiusa e il nodo marcato offline.
    Finche' il nodo non si e' registrato non ha un peerId, quindi gli unici comandi accettati sono
    REGISTER e DISCONNECT. Se un messaggio e' malformato (argomenti mancanti o porta non numerica)
    viene risposto ERR senza chiudere la connessione, cosi' un singolo messaggio sbagliato non
    fa terminare il thread che gestisce il nodo.
    */
    private boolean gestioneMessaggio(String riga, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        String[] campi = riga.trim().split("\\s+");
        String comando = campi[0];

        if (peerId == null && !comando.equals(Protocol.REGISTER) && !comando.equals(Protocol.DISCONNECT)) {
            scrittore.println(Protocol.ERR + " nodo non registrato");
            return true;
        }

        try {
            switch (comando) {

                case Protocol.REGISTER -> {
                    if (peerId != null) {
                        scrittore.println(Protocol.ERR + " nodo gia' registrato come " + peerId);
                        return true;
                    }
                    String host = campi[1];
                    int port = Integer.parseInt(campi[2]);
                    List<String> rilevazioni = (campi.length > 3 && !campi[3].equals("-"))
                            ? List.of(campi[3].split(",")) : List.of();
                    peerId = tabella.registraNodo(host, port, rilevazioni);

                    scrittore.println(Protocol.OK + " " + peerId);
                }

                case Protocol.ADD -> {
                    tabella.addRilevazione(peerId, campi[1]);
                    scrittore.println(Protocol.OK);
                }

                case Protocol.LIST -> {
                    Map<String, List<String>> tutte = tabella.listaRilevazioni();
                    for (Map.Entry<String, List<String>> e : tutte.entrySet()) {
                        scrittore.println(e.getKey() + " " + String.join(", ", e.getValue()));
                    }
                    scrittore.println(Protocol.END);
                }

                case Protocol.NODES -> {
                    for (InfoPeer p : tabella.activeNodes(peerId)) {
                        scrittore.println(p.getPeerId() + " " + p.getHost() + " " + p.getPort());
                    }
                    scrittore.println(Protocol.END);
                }

                case Protocol.WHOHAS -> {
                    for (InfoPeer p : tabella.nodiConRilevazione(campi[1])) {
                        scrittore.println(p.getPeerId() + " " + (p.isOnline() ? "online" : "offline"));
                    }
                    scrittore.println(Protocol.END);
                }

                case Protocol.DOWNLOAD -> {
                    return gestisciSessioneDownload(campi[1], lettore, scrittore);
                }

                case Protocol.DISCONNECT -> {
                    scrittore.println(Protocol.OK);
                    return false;
                }

                default -> {
                    scrittore.println(Protocol.ERR + " comando sconosciuto");
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            scrittore.println(Protocol.ERR + " messaggio malformato");
        }
        return true;
    }

    /*
    Gestisce dall'inizio alla fine una sessione di download per una rilevazione.
    All'apertura della sessione l'aggregatore genera il token di accesso, che resta valido solo
    per questa sessione: il richiedente lo riceve insieme al primo fornitore proposto e deve
    ripresentarlo in ogni RETRY e nel DONE finale, insieme all'id del fornitore che gli e' stato
    proposto per ultimo. Un messaggio con token o fornitore diversi viene rifiutato con ERR e la
    sessione termina, cosi' il richiedente non puo' far rimuovere dalla tabella le entry di nodi
    che l'aggregatore non gli ha mai proposto. Il token viene rilasciato con il DONE (download
    riuscito), oppure quando l'aggregatore risponde UNAVAILABLE (nessun altro fornitore).
    Il richiedente viene escluso dai fornitori e la sua eventuale entry per quella rilevazione
    viene rimossa: se chiede di scaricarla significa che in locale non la possiede.
    Ad ogni RETRY il fornitore che non ha funzionato viene escluso, la sua entry viene eliminata
    dalla tabella e ne viene cercato un altro. Ogni tentativo (riuscito o fallito) viene
    registrato nel RegistroDownload, cosi' il comando "log" mostra tutte le richieste di download.
    Restituisce false solo se durante la sessione il nodo si e' disconnesso, per chiudere la connessione.
    */
    private boolean gestisciSessioneDownload(String rilevazione, BufferedReader lettore, PrintWriter scrittore) throws IOException {
        String token = UUID.randomUUID().toString();
        /* mantiene un insieme di nodi gia' provati e falliti, cosi' da non riproporli in caso di RETRY. */
        Set<String> esclusi = new HashSet<>();
        esclusi.add(peerId);
        tabella.removeEntryRilevazione(peerId, rilevazione);

        InfoPeer fornitore = tabella.selectProvider(rilevazione, esclusi);
        if (fornitore == null) {
            registro.registra(rilevazione, "-", peerId, false);
            scrittore.println(Protocol.UNAVAILABLE);
            return true;
        }
        inviaFornitore(scrittore, token, fornitore);

        String riga;
        while ((riga = lettore.readLine()) != null) {
            String[] campi = riga.trim().split("\\s+");
            String comando = campi[0];

            if (comando.equals(Protocol.DISCONNECT)) {
                registro.registra(rilevazione, fornitore.getPeerId(), peerId, false);
                scrittore.println(Protocol.OK);
                return false;
            }

            if (!comando.equals(Protocol.DONE) && !comando.equals(Protocol.RETRY)) {
                registro.registra(rilevazione, fornitore.getPeerId(), peerId, false);
                scrittore.println(Protocol.ERR + " comando non valido durante il download");
                return true;
            }

            if (campi.length < 3 || !campi[1].equals(token) || !campi[2].equals(fornitore.getPeerId())) {
                registro.registra(rilevazione, fornitore.getPeerId(), peerId, false);
                scrittore.println(Protocol.ERR + " token di accesso non valido");
                return true;
            }

            if (comando.equals(Protocol.DONE)) {
                tabella.addRilevazione(peerId, rilevazione);
                registro.registra(rilevazione, fornitore.getPeerId(), peerId, true);
                scrittore.println(Protocol.OK);
                return true;
            }

            /* RETRY: il fornitore proposto non ha fornito la rilevazione */
            String fallito = fornitore.getPeerId();
            registro.registra(rilevazione, fallito, peerId, false);
            esclusi.add(fallito);
            tabella.removeEntryRilevazione(fallito, rilevazione);

            fornitore = tabella.selectProvider(rilevazione, esclusi);
            if (fornitore == null) {
                registro.registra(rilevazione, "-", peerId, false);
                scrittore.println(Protocol.UNAVAILABLE);
                return true;
            }
            inviaFornitore(scrittore, token, fornitore);
        }

        /* Il nodo ha chiuso la connessione nel mezzo della sessione. */
        registro.registra(rilevazione, fornitore.getPeerId(), peerId, false);
        return false;
    }

    /*
    Invia al richiedente il messaggio PEER con il token di accesso e i dati (id, host e porta)
    del nodo da cui scaricare la rilevazione.
    */
    private void inviaFornitore(PrintWriter scrittore, String token, InfoPeer fornitore) {
        scrittore.println(Protocol.PEER + " " + token + " " + fornitore.getPeerId() + " " + fornitore.getHost() + " " + fornitore.getPort());
    }

}
