import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

/**
 * Punto di ingresso del nodo sensore. Avviato con indirizzo e porta
 * dell'aggregatore, es. "java Client 127.0.0.1 9000".
 * Terzo parametro opzionale: nome del nodo (cartella di storage), utile per
 * pre-allocare le rilevazioni. Se assente ne viene generato uno.
 *
 * Orchestra le tre attivita' concorrenti del nodo:
 *   - PeerServer su thread di background (serve gli altri nodi);
 *   - connessione persistente all'aggregatore (AggregatorLink);
 *   - console interattiva sul thread principale.
 * Cosi' la sessione interattiva non interrompe il funzionamento in rete.
 *
 * OWNER: Membro B (Client - base). Il comando "download" e' delegato al
 * Downloader (Membro C).
 */

/**
 * Classe principale che rappresenta un singolo "nodo" (un dispositivo/client) della rete.
 * Si occupa di coordinare tre attività in contemporanea:
 * 1. Rispondere alle richieste di altri nodi (server P2P).
 * 2. Mantenere i contatti con il server centrale (aggregatore).
 * 3. Leggere ed eseguire i comandi scritti dall'utente sul terminale.
 */
public class Client {

    public static void main(String[] args) {
        
        // --- CONTROLLO ED ESTRAZIONE PARAMETRI DA TERMINALE ---
        
        // Se l'utente non ha passato almeno IP e Porta, mostra l'errore ed esce
        if (args.length < 2) {
            System.out.println("Uso: java Client <ip_aggregatore> <porta_aggregatore> [nome_nodo]");
            return;
        }

        // Legge l'indirizzo IP del server centrale
        String host = args[0];
        
        // Converte la stringa della porta in un numero intero
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Porta non valida.");
            return; // Se la porta non è un numero valido, interrompe il programma
        }

        // Se l'utente ha inserito un nome per il nodo usa quello,
        // altrimenti ne genera uno unico automatico (es: node-a1b2c3d4)
        String nodeName = (args.length >= 3) ? args[2]
                : "node-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // --- 1. INIZIALIZZAZIONE ARCHIVIO LOCALE ---
            // Crea il gestore della cartella/memoria locale per questo nodo
            LocalStore store = new LocalStore(nodeName);

            // --- 2. AVVIO DEL SERVER P2P (BACKGROUND) ---
            // Prepara il server che risponderà agli altri nodi quando chiederanno dati
            PeerServer peerServer = new PeerServer(store);
            
            // Inserisce il server in un thread separato così non blocca il resto del programma
            Thread peerThread = new Thread(peerServer, "PeerServer-Thread");
            
            // "Daemon" significa che questo thread si chiuderà
            // automaticamente quando il programma principale finisce
            peerThread.setDaemon(true);
            peerThread.start(); // Avvia effettivamente il server in sottofondo

            // --- 3. CONNESSIONE AL SERVER CENTRALE (AGGREGATORE) ---
            AggregatorLink aggregator;
            try {
                // Tenta di connettersi al server centrale tramite IP e Porta
                aggregator = new AggregatorLink(host, port);
            } catch (IOException e) {
                // Se la connessione fallisce, spegne il server P2P appena aperto ed esce
                System.out.println("Errore: aggregatore non raggiungibile su " + host + ":" + port);
                peerServer.shutdown();
                return;
            }

            // --- 4. REGISTRAZIONE DEL NODO ---
            // Dice all'aggregatore: "Sono online! Ecco la mia IP,
            // la mia porta P2P e l'elenco dei file che possiedo"
            String peerId = aggregator.register(
                    aggregator.localAddress(), peerServer.getPort(), store.listNames());
            
            System.out.println("Connesso all'aggregatore come " + peerId
                    + " (storage: " + nodeName + ", porta peer: " + peerServer.getPort() + ").");

            // --- 5. AVVIO DEL GESTORE DOWNLOAD E CONSOLE INTERATTIVA ---
            // Prepara il componente per scaricare file da altri nodi
            Downloader downloader = new Downloader(aggregator, store);
            
            // Passa il controllo alla console interattiva per
            // l'utente (si ferma qui finché l'utente non esce)
            console(store, aggregator, downloader, peerServer);

        } catch (IOException e) {
            System.err.println("Errore di avvio del nodo: " + e.getMessage());
        }
    }

    /**
     * Gestisce la lettura dei comandi digitati dall'utente sul terminale.
     */
    private static void console(LocalStore store, AggregatorLink aggregator,
                                Downloader downloader, PeerServer peerServer) {
        System.out.println("\n--- Console Nodo Avviata ---");
        System.out.println("Comandi disponibili: listdata local | listdata remote | add <nome> <contenuto> | download <nome> | quit\n");

        // Apre il canale di lettura da tastiera (System.in)
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            
            // Ciclo infinito: resta in attesa che l'utente scriva un comando e prema Invio
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim(); // Rimuove spazi vuoti inutili all'inizio e alla fine

                // Se l'utente ha premuto solo Invio senza scrivere nulla, salta il giro
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    // COMANDO 1: Mostra i file salvati sul proprio computer
                    if (trimmed.equals("listdata local")) {
                        System.out.println("Risorse locali:");
                        List<String> localResources = store.listNames();
                        
                        if (localResources.isEmpty()) {
                            System.out.println("  (nessuna risorsa locale presente)");
                        } else {
                            for (String r : localResources) {
                                System.out.println("- " + r);
                            }
                        }

                    // COMANDO 2: Chiede all'aggregatore la lista dei file disponibili su TUTTI gli altri nodi
                    } else if (trimmed.equals("listdata remote")) {
                        System.out.println("Risorse di rete:");
                        List<String> remoteResources = aggregator.listRemote();
                        
                        if (remoteResources.isEmpty()) {
                            System.out.println("  (nessuna risorsa trovata sulla rete)");
                        } else {
                            for (String l : remoteResources) {
                                // Separa il nome della risorsa dalle informazioni sul nodo che la possiede
                                String[] p = l.split("\\s+", 2);
                                System.out.println("- " + p[0] + ": " + (p.length > 1 ? p[1] : ""));
                            }
                        }

                    // COMANDO 3: Salva un nuovo file in locale e avvisa il server centrale
                    } else if (trimmed.startsWith("add ")) {
                        // Separa il comando in 3 parti: ["add", "nomeFile", "contenuto del file"]
                        String[] parts = trimmed.split("\\s+", 3);
                        if (parts.length < 3) {
                            System.out.println("Uso: add <nome> <contenuto>");
                            continue;
                        }
                        String resourceName = parts[1];
                        String content = parts[2];

                        // 1. Salva il file sul proprio disco
                        store.add(resourceName, content);
                        // 2. Avvisa l'aggregatore: "Ehi, ora ho anche questo nuovo file"
                        aggregator.notifyAdd(resourceName);
                        System.out.println("Rilevazione '" + resourceName + "' aggiunta e notificata all'aggregatore.");

                    // COMANDO 4: Scarica un file presente su un altro nodo della rete
                    } else if (trimmed.startsWith("download ")) {
                        String[] parts = trimmed.split("\\s+", 2);
                        if (parts.length < 2) {
                            System.out.println("Uso: download <nome>");
                            continue;
                        }
                        // Chiama il Downloader per cercare ed estrarre la risorsa da un altro nodo
                        downloader.download(parts[1]);

                    // COMANDO 5: Chiude l'applicazione
                    } else if (trimmed.equals("quit")) {
                        shutdownNode(peerServer, aggregator); // Spegne le connessioni
                        return; // Esce dal metodo console (e termina il programma)

                    } else {
                        System.out.println("Comando sconosciuto: '" + trimmed + "'");
                    }
                } catch (IOException e) {
                    System.out.println("Errore durante l'esecuzione del comando: " + e.getMessage());
                }
                
                System.out.print("> "); // Ristampa il simbolo del prompt per la prossima istruzione
            }
        } catch (IOException e) {
            System.err.println("Errore di I/O sulla console: " + e.getMessage());
        } finally {
            // Viene eseguito sempre quando si chiude il flusso di input (es. spegnimento imprevisto)
            shutdownNode(peerServer, aggregator);
        }
    }

    /**
     * Procedura di pulizia per chiudere le connessioni aperte in modo pulito ed evitare blocchi.
     */
    private static void shutdownNode(PeerServer peerServer, AggregatorLink aggregator) {
        // Chiude il server P2P di background se attivo
        if (peerServer != null) {
            peerServer.shutdown();
        }
        // Disconnette la comunicazione con l'aggregatore centrale se attiva
        if (aggregator != null) {
            try {
                aggregator.disconnect();
            } catch (IOException ignored) {
                // Ignora eventuali errori mentre si sta scollegando
            }
        }
        System.out.println("Nodo arrestato correttamente.");
    }
}