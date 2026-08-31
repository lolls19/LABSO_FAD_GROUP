import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.List;

/*
 * Questa e' la classe di avvio del nodo sensore: si lancia passando indirizzo e
 * porta dell'aggregatore (es. "java Client 127.0.0.1 9000"), ed eventualmente un
 * terzo argomento con il nome del nodo, usato come cartella di storage; se non
 * viene indicato ne genera uno automaticamente. Il metodo main mette in piedi
 * tutte le parti del nodo: l'archivio locale delle rilevazioni, il server P2P che
 * risponde alle richieste degli altri nodi (su un thread di background), la
 * connessione persistente con l'aggregatore, il componente che gestisce i
 * download, e infine passa il controllo alla console interattiva sul thread
 * principale, cosi' l'utente puo' usare il nodo mentre questo continua a
 * funzionare in rete.
 */
public class Client {

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
            } catch (IOException e) {
                System.out.println("Errore: aggregatore non raggiungibile su " + host + ":" + port);
                peerServer.shutdown();
                return;
            }

            String peerId = aggregatore.register(
                    aggregatore.localAddress(), peerServer.getPort(), store.listNames());

            System.out.println("Connesso all'aggregatore come " + peerId
                    + " (storage: " + nodeName + ", porta peer: " + peerServer.getPort() + ").");

            Downloader downloader = new Downloader(aggregatore, store);

            console(store, aggregatore, downloader, peerServer);

        } catch (IOException e) {
            System.err.println("Errore di avvio del nodo: " + e.getMessage());
        }
    }

    /*
     * Legge i comandi digitati dall'utente e li esegue: "listdata local" mostra le rilevazioni
     * possedute dal nodo, "listdata remote" chiede all'aggregatore cosa e' disponibile su tutta la
     * rete, "add <nome> <contenuto>" salva una nuova rilevazione e la notifica all'aggregatore,
     * "download <nome>" prova a scaricarla da un altro nodo, "quit" chiude il programma. Le righe
     * vuote vengono ignorate e qualunque altro comando viene segnalato come sconosciuto. Qualunque
     * sia il modo in cui si esce dal ciclo (quit oppure chiusura imprevista dell'input), il blocco
     * finally si occupa di spegnere il nodo in modo pulito tramite shutdownNode().
     */
    private static void console(LocalStore store, AggregatorLink aggregatore,
                                Downloader downloader, PeerServer peerServer) {
        System.out.println("\n--- Console Nodo Avviata ---");
        System.out.println("Comandi disponibili: listdata local | listdata remote | add <nome> <contenuto> | download <nome> | quit\n");

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String riga;

            while ((riga = console.readLine()) != null) {
                String trimmed = riga.trim();

                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    if (trimmed.equals("listdata local")) {
                        System.out.println("Risorse locali:");
                        List<String> rilevazioniLocali = store.listNames();

                        if (rilevazioniLocali.isEmpty()) {
                            System.out.println("  (nessuna risorsa locale presente)");
                        } else {
                            for (String r : rilevazioniLocali) {
                                System.out.println("- " + r);
                            }
                        }

                    } else if (trimmed.equals("listdata remote")) {
                        System.out.println("Risorse di rete:");
                        List<String> rilevazioniRemote = aggregatore.listRemote();

                        if (rilevazioniRemote.isEmpty()) {
                            System.out.println("  (nessuna risorsa trovata sulla rete)");
                        } else {
                            for (String rigaRemota : rilevazioniRemote) {
                                String[] campi = rigaRemota.split("\\s+", 2);
                                System.out.println("- " + campi[0] + ": " + (campi.length > 1 ? campi[1] : ""));
                            }
                        }

                    } else if (trimmed.startsWith("add ")) {
                        String[] campi = trimmed.split("\\s+", 3);
                        if (campi.length < 3) {
                            System.out.println("Uso: add <nome> <contenuto>");
                            continue;
                        }
                        String nomeRilevazione = campi[1];
                        String contenuto = campi[2];

                        store.add(nomeRilevazione, contenuto);
                        aggregatore.notifyAdd(nomeRilevazione);
                        System.out.println("Rilevazione '" + nomeRilevazione + "' aggiunta e notificata all'aggregatore.");

                    } else if (trimmed.startsWith("download ")) {
                        String[] campi = trimmed.split("\\s+", 2);
                        if (campi.length < 2) {
                            System.out.println("Uso: download <nome>");
                            continue;
                        }
                        downloader.download(campi[1]);

                    } else if (trimmed.equals("quit")) {
                        return;

                    } else {
                        System.out.println("Comando sconosciuto: '" + trimmed + "'");
                    }
                } catch (IOException e) {
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
     * Chiude in modo ordinato tutte le risorse aperte dal nodo: ferma il server P2P e si
     * disconnette dall'aggregatore, ignorando eventuali errori nel farlo (tanto il nodo sta
     * comunque per chiudersi). Viene chiamato sempre, sia quando l'utente digita "quit" sia in
     * caso di chiusura imprevista.
     */
    private static void shutdownNode(PeerServer peerServer, AggregatorLink aggregatore) {
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
