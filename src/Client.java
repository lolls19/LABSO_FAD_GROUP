import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/*
Questa e' la classe di avvio del nodo sensore: si lancia passando indirizzo e
porta dell'aggregatore (es. "java Client 127.0.0.1 9000"), ed eventualmente un
terzo argomento con il nome del nodo, usato come cartella di storage; se non
viene indicato ne genera uno automaticamente. Il metodo main mette in piedi
tutte le parti del nodo: l'archivio locale delle rilevazioni, il server P2P che
risponde alle richieste degli altri nodi (su un thread di background), la
connessione persistente con l'aggregatore, il componente che gestisce i
download, e infine passa il controllo alla console interattiva sul thread
principale, cosi' l'utente puo' usare il nodo mentre questo continua a
funzionare in rete.
*/
public class Client {

    /*
    Diventa true alla prima chiamata di shutdownNode(), cosi' la chiusura del nodo viene eseguita
    una sola volta anche se viene richiesta sia dalla console sia dal thread di chiusura (Ctrl+C).
    */
    private static final AtomicBoolean arrestato = new AtomicBoolean(false);

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Uso: java Client <ip_aggregatore> <porta_aggregatore> [nome_nodo]");
            return;
        }

        String host = args[0];

        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Porta non valida.");
            return;
        }

        String nodeName = (args.length >= 3) ? args[2]
                : "node-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            LocalStore store = new LocalStore(nodeName);

            PeerServer peerServer = new PeerServer(store);

            Thread peerThread = new Thread(peerServer, "PeerServer-Thread");

            peerThread.setDaemon(true);
            peerThread.start();

            AggregatorLink aggregatore;
            try {
                aggregatore = new AggregatorLink(host, port);
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Errore: aggregatore non raggiungibile su " + host + ":" + port);
                peerServer.shutdown();
                return;
            }

            /*
            Se il programma viene chiuso senza usare "quit" (per esempio con Ctrl+C), la JVM esegue
            questo thread prima di terminare: cosi' il nodo comunica comunque all'aggregatore che
            si sta disconnettendo dalla rete.
            */
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownNode(peerServer, aggregatore)));

            String peerId = aggregatore.register(
                    aggregatore.localAddress(), peerServer.getPort(), store.listNames());

            System.out.println("Connesso all'aggregatore come " + peerId
                    + " (storage: " + nodeName + ", porta peer: " + peerServer.getPort() + ").");

            Downloader downloader = new Downloader(aggregatore, store);

            console(store, aggregatore, downloader, peerServer);

        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Errore di avvio del nodo: " + e.getMessage());
        }
    }

    /*
    Legge i comandi digitati dall'utente e li esegue:
     - "listdata local" mostra le rilevazioni possedute dal nodo;
     - "listdata remote" chiede all'aggregatore tutte le rilevazioni della rete con i nodi che le possiedono;
     - "listnodes" chiede all'aggregatore l'elenco degli altri nodi sensore attivi;
     - "whohas <nome>" chiede all'aggregatore quali nodi possiedono una determinata rilevazione;
     - "add <nome> <contenuto>" salva una nuova rilevazione e la notifica all'aggregatore;
     - "download <nome>" scarica la rilevazione da un altro nodo scelto dall'aggregatore;
     - "quit" chiude il programma.
    Le righe vuote vengono ignorate e qualunque altro comando viene segnalato come sconosciuto.
    Gli errori di un singolo comando (connessione con l'aggregatore persa, nome di rilevazione
    non valido...) vengono stampati senza chiudere il nodo, che continua a servire gli altri nodi.
    Qualunque sia il modo in cui si esce dal ciclo (quit oppure chiusura imprevista dell'input), il
    blocco finally si occupa di spegnere il nodo in modo pulito tramite shutdownNode().
    */
    private static void console(LocalStore store, AggregatorLink aggregatore,
                                Downloader downloader, PeerServer peerServer) {
        System.out.println("\n--- Console Nodo Avviata ---");
        System.out.println("Comandi disponibili: listdata local | listdata remote | listnodes | whohas <nome> | add <nome> <contenuto> | download <nome> | quit\n");
        System.out.print("> ");

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String riga;

            while ((riga = console.readLine()) != null) {
                String trimmed = riga.trim();

                if (trimmed.isEmpty()) {
                    System.out.print("> ");
                    continue;
                }

                String[] campi = trimmed.split("\\s+");

                try {
                    if (trimmed.equals("listdata local")) {
                        System.out.println("Risorse:");
                        List<String> rilevazioniLocali = store.listNames();

                        if (rilevazioniLocali.isEmpty()) {
                            System.out.println("  (nessuna risorsa locale presente)");
                        } else {
                            for (String r : rilevazioniLocali) {
                                System.out.println("- " + r);
                            }
                        }

                    } else if (trimmed.equals("listdata remote")) {
                        System.out.println("Risorse:");
                        List<String> rilevazioniRemote = aggregatore.listRemote();

                        if (rilevazioniRemote.isEmpty()) {
                            System.out.println("  (nessuna risorsa trovata sulla rete)");
                        } else {
                            for (String rigaRemota : rilevazioniRemote) {
                                String[] parti = rigaRemota.split("\\s+", 2);
                                System.out.println("- " + parti[0] + ": " + (parti.length > 1 ? parti[1] : ""));
                            }
                        }

                    } else if (trimmed.equals("listnodes")) {
                        System.out.println("Nodi attivi:");
                        List<String> nodi = aggregatore.listNodes();

                        if (nodi.isEmpty()) {
                            System.out.println("  (nessun altro nodo attivo)");
                        } else {
                            for (String rigaNodo : nodi) {
                                String[] parti = rigaNodo.split("\\s+");
                                System.out.println("- " + parti[0] + " (" + parti[1] + ":" + parti[2] + ")");
                            }
                        }

                    } else if (campi[0].equals("whohas")) {
                        if (campi.length != 2) {
                            System.out.println("Uso: whohas <nome>");
                        } else if (!LocalStore.nomeValido(campi[1])) {
                            System.out.println("Nome della rilevazione non valido: '" + campi[1] + "'");
                        } else {
                            System.out.println("Nodi che possiedono " + campi[1] + ":");
                            List<String> possessori = aggregatore.whoHas(campi[1]);

                            if (possessori.isEmpty()) {
                                System.out.println("  (nessun nodo possiede questa rilevazione)");
                            } else {
                                for (String rigaNodo : possessori) {
                                    String[] parti = rigaNodo.split("\\s+");
                                    String stato = (parti.length > 1 && parti[1].equals("offline")) ? " (offline)" : "";
                                    System.out.println("- " + parti[0] + stato);
                                }
                            }
                        }

                    } else if (campi[0].equals("add")) {
                        String[] parti = trimmed.split("\\s+", 3);
                        if (parti.length < 3) {
                            System.out.println("Uso: add <nome> <contenuto>");
                        } else {
                            String nomeRilevazione = parti[1];
                            String contenuto = parti[2];

                            if (store.add(nomeRilevazione, contenuto)) {
                                aggregatore.notifyAdd(nomeRilevazione);
                                System.out.println("Rilevazione '" + nomeRilevazione + "' aggiunta e notificata all'aggregatore.");
                            } else {
                                System.out.println("Esiste gia' una rilevazione chiamata '" + nomeRilevazione + "' in questo nodo.");
                            }
                        }

                    } else if (campi[0].equals("download")) {
                        if (campi.length != 2) {
                            System.out.println("Uso: download <nome>");
                        } else if (!LocalStore.nomeValido(campi[1])) {
                            System.out.println("Nome della rilevazione non valido: '" + campi[1] + "'");
                        } else {
                            downloader.download(campi[1]);
                        }

                    } else if (trimmed.equals("quit")) {
                        return;

                    } else {
                        System.out.println("Comando sconosciuto: '" + trimmed + "'");
                    }
                } catch (IOException | IllegalArgumentException e) {
                    System.out.println("Errore durante l'esecuzione del comando: " + e.getMessage());
                }

                System.out.print("> ");
            }
        } catch (IOException e) {
            System.err.println("Errore di I/O sulla console: " + e.getMessage());
        } finally {
            shutdownNode(peerServer, aggregatore);
        }
    }

    /*
    Chiude in modo ordinato tutte le risorse aperte dal nodo: ferma il server P2P e comunica
    all'aggregatore che si sta disconnettendo, ignorando eventuali errori nel farlo (tanto il nodo
    sta comunque per chiudersi). Viene chiamato sia quando l'utente digita "quit" o l'input della
    console si chiude, sia dal thread di chiusura della JVM (Ctrl+C); grazie ad "arrestato" le
    operazioni vengono eseguite una sola volta.
    */
    private static void shutdownNode(PeerServer peerServer, AggregatorLink aggregatore) {
        if (!arrestato.compareAndSet(false, true)) {
            return;
        }
        if (peerServer != null) {
            peerServer.shutdown();
        }
        if (aggregatore != null) {
            try {
                aggregatore.disconnect();
            } catch (IOException ignored) {
            }
        }
        System.out.println("Nodo arrestato correttamente.");
    }
}
