import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.List;

/**
 * Gestisce il ciclo di vita di un nodo della rete P2P.
 * Inizializza lo storage locale, avvia il server per gestire le richieste degli altri peer in background,
 * si registra presso l'aggregatore centrale e fornisce un'interfaccia a riga di comando per l'utente.
 */
public class Client {

    /**
     * Punto di ingresso dell'applicazione: valida i parametri di avvio, predispone le componenti
     * trasversali (storage, PeerServer, connessione all'aggregatore) e avvia la console di comando.
     */
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

            AggregatorLink aggregator;
            try {
                aggregator = new AggregatorLink(host, port);
            } catch (IOException e) {
                System.out.println("Errore: aggregatore non raggiungibile su " + host + ":" + port);
                peerServer.shutdown();
                return;
            }

            String peerId = aggregator.register(
                    aggregator.localAddress(), peerServer.getPort(), store.listNames());
            
            System.out.println("Connesso all'aggregatore come " + peerId
                    + " (storage: " + nodeName + ", porta peer: " + peerServer.getPort() + ").");

            Downloader downloader = new Downloader(aggregator, store);
            
            console(store, aggregator, downloader, peerServer);

        } catch (IOException e) {
            System.err.println("Errore di avvio del nodo: " + e.getMessage());
        }
    }

    /**
     * Mantiene un ciclo REPL sul thread principale per interpretare ed eseguire i comandi utente 
     * (consultazione locale/remota, pubblicazione file e download P2P) senza bloccare i thread di rete.
     */
    private static void console(LocalStore store, AggregatorLink aggregator,
                                Downloader downloader, PeerServer peerServer) {
        System.out.println("\n--- Console Nodo Avviata ---");
        System.out.println("Comandi disponibili: listdata local | listdata remote | add <nome> <contenuto> | download <nome> | quit\n");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();

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
                        List<String> rilevazioniRemote = aggregator.listRemote();

                        if (rilevazioniRemote.isEmpty()) {
                            System.out.println("  (nessuna risorsa trovata sulla rete)");
                        } else {
                            for (String l : rilevazioniRemote) {
                                String[] p = l.split("\\s+", 2);
                                System.out.println("- " + p[0] + ": " + (p.length > 1 ? p[1] : ""));
                            }
                        }

                    } else if (trimmed.startsWith("add ")) {
                        String[] parts = trimmed.split("\\s+", 3);
                        if (parts.length < 3) {
                            System.out.println("Uso: add <nome> <contenuto>");
                            continue;
                        }
                        String nomeRilevazione = parts[1];
                        String contenuto = parts[2];

                        store.add(nomeRilevazione, contenuto);
                        aggregator.notifyAdd(nomeRilevazione);
                        System.out.println("Rilevazione '" + nomeRilevazione + "' aggiunta e notificata all'aggregatore.");

                    } else if (trimmed.startsWith("download ")) {
                        String[] parts = trimmed.split("\\s+", 2);
                        if (parts.length < 2) {
                            System.out.println("Uso: download <nome>");
                            continue;
                        }
                        downloader.download(parts[1]);

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
            shutdownNode(peerServer, aggregator);
        }
    }

    /**
     * Rilascia le risorse di rete arrestando il server P2P locale e notificando la disconnessione
     * all'aggregatore per garantire una chiusura pulita e senza socket orfani.
     */
    private static void shutdownNode(PeerServer peerServer, AggregatorLink aggregator) {
        if (peerServer != null) {
            peerServer.shutdown();
        }
        if (aggregator != null) {
            try {
                aggregator.disconnect();
            } catch (IOException ignored) {
            }
        }
        System.out.println("Nodo arrestato correttamente.");
    }
}