import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/*
 * Questa e' la classe di avvio dell'aggregatore, legge la porta da riga di
 * comando, crea le due risorse condivise di tutto il programma (la tabella delle
 * rilevazioni, e il registro dei download), avvia il server di ascolto su un thread
 * di background e infine si mette a gestire la console interattiva sul thread principale, 
 * dove l'utente puo' digitare "listdata" per vedere le rilevazioni disponibili, "log" per lo
 * storico dei download, oppure "quit" per spegnere l'aggregatore.
 */
public class Master {

    // Il metodo main() legge la porta da riga di comando, crea le due risorse condivise di tutto il programma
    // (la tabella delle rilevazioni, e il registro dei download), avvia il server di ascolto su un thread
    // di background e infine richiama il metodo console() per gestire la console interattiva sul thread principale.
    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Uso: java Master <porta>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Porta non valida.");
            return;
        }

        TabellaRilevazioni tabella = new TabellaRilevazioni();
        RegistroDownload registro = new RegistroDownload();

        ServerAggregatore server = new ServerAggregatore(port, tabella, registro);
        Thread threadServer = new Thread(server);
        threadServer.setDaemon(true);
        threadServer.start();

        console(tabella, registro, server);
    }

    // il metodo console() legge i comandi dell'utente dalla console e li interpreta:
    //  "listdata" stampa le rilevazioni disponibili,
    //  "log" stampa lo storico dei download, "quit" ferma il server e termina il programma.
    //  Se l'utente digita un comando sconosciuto stampa un messaggio di errore.
    private static void console(TabellaRilevazioni tabella, RegistroDownload registro, ServerAggregatore server) {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String riga;
            while ((riga = console.readLine()) != null) {
                switch (riga.trim()) {
                    case "listdata" -> stampaRisorse(tabella);
                    case "log"      -> stampaLog(registro);
                    case "quit"     -> {
                        server.shutdown();
                        System.out.println("Aggregatore arrestato.");
                        return;
                    }
                    case ""         -> { }
                    default         -> System.out.println("Comando sconosciuto.");
                }
            }
        } catch (Exception e) {
            System.err.println("Errore console: " + e.getMessage());
        }
    }

    // Stampa l'elenco di tutte le rilevazioni conosciute dall'aggregatore, ciascuna seguita dai
    // nodi che la possiedono; se non ce n'e' nessuna stampa "(nessuna)".
    private static void stampaRisorse(TabellaRilevazioni tabella) {
        Map<String, List<String>> TutteRilevazioni = tabella.listaRilevazioni();
        System.out.println("Risorse:");
        if (TutteRilevazioni.isEmpty()) {
            System.out.println("(nessuna)");
            return;
        }
        for (Map.Entry<String, List<String>> r : TutteRilevazioni.entrySet()) {
            System.out.println("- " + r.getKey() + ": " + String.join(", ", r.getValue()));
        }
    }

    // Stampa lo storico di tutti i download registrati finora (riusciti e falliti); se il registro
    // e' vuoto stampa "(nessuna)".
    private static void stampaLog(RegistroDownload registro) {
        System.out.println("Risorse scaricate:");
        List<RegistroDownload.Entry> ListaDownload = registro.getEntries();
        if (ListaDownload.isEmpty()) {
            System.out.println("(nessuna)");
            return;
        }
        for (RegistroDownload.Entry  d : ListaDownload) System.out.println(d);
    }
}
