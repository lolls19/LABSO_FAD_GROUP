import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/*
Si occupa di avviare l'aggregatore e la console interattiva.


 la classe fa tre cose principali:
    1) Controlla i parametri della riga di comando (deve esserci esattamente una porta)
    2) Crea le due risorse condivise dell'aggregatore:
        - la tabella delle rilevazioni (chi possiede cosa)
        - il registro dei download (per il comando log)
    3) Crea il server di ascolto e lo avvia su un thread DI BACKGROUND.
       Il thread principale si occupa della console interattiva.

 i comandi della console sono:
    - listdata: stampa le rilevazioni e i nodi che le possiedono
    - log: stampa lo storico dei download registrati
    - quit: chiude l'aggregatore
 */
 
 
public class Master {

    public static void main(String[] args) {

       // si controlla che ci sia esattamente un parametro (la porta) e che sia un numero valido.
        if (args.length != 1) {
            System.out.println("Uso: java Master <porta>");
            return;
        }

        // si converte il parametro in un numero intero (la porta) e si gestisce l'eventuale 
        // eccezione se non è un numero valido.
        int porta;
        try {
            porta = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Porta non valida.");
            return;
        }

        // si creano le due risorse condivise dell'aggregatore: la tabella delle rilevazioni 
        // e il registro dei download
        TabellaRilevazioni tabella = new TabellaRilevazioni();
        RegistroDownload registro = new RegistroDownload();

        // si crea il server di ascolto e lo avvia su un thread di background, così l'aggregatore può
        //  servire più nodi contemporaneamente
        
        ServerAggregatore server = new ServerAggregatore(porta, tabella, registro);
        Thread threadServer = new Thread(server);
        //si imposta il thread come daemon, così non impedisce la chiusura del programma quando si esce dalla console
        threadServer.setDaemon(true);
        // si avvia il thread del server di ascolto
        threadServer.start();

        //  Il thread principale si occupa della console interattiva.
        //    (Il server, intanto, ascolta i nodi sull'altro thread.)
        console(tabella, registro, server);
    }

    /*
     Il metodo console() legge i comandi dalla console e li esegue. I comandi sono:
        - listdata: stampa le rilevazioni e i nodi che le possiedono
        - log: stampa lo storico dei download registrati
        - quit: chiude l'aggregatore

        Lo fa in un ciclo finché non si digita "quit" o si chiude l'input della console.
     */
    private static void console(TabellaRilevazioni tabella, RegistroDownload registro, ServerAggregatore server) {
        // si usa try-with-resources per chiudere automaticamente il BufferedReader alla fine del blocco
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String riga;
             // legge i comandi dalla console finché non si digita "quit" o si chiude l'input della console
            while ((riga = console.readLine()) != null) {
                switch (riga.trim()) {                
                    case "listdata" -> stampaRisorse(tabella);
                    case "log"      -> stampaLog(registro);
                    case "quit"     -> {
                        server.shutdown();             // ferma il server di ascolto
                        System.out.println("Aggregatore arrestato.");
                        return;                        // esce dalla console (e dal programma)
                    }
                    case ""         -> { }             // riga vuota (invio a vuoto): non fa nulla
                    default         -> System.out.println("Comando sconosciuto.");
                }
            }
        } catch (Exception e) {
            // se c'è un errore nella console, lo stampa e termina il programma
            System.err.println("Errore console: " + e.getMessage());
        }
    }

    // Comando "listdata": stampa ogni rilevazione con i nodi che la possiedono. 
    private static void stampaRisorse(TabellaRilevazioni tabella) {
        // si ottiene la mappa di tutte le rilevazioni e i nodi che le possiedono, e si stampa in formato leggibile
        Map<String, List<String>> tutte = tabella.listaRilevazioni();
        System.out.println("Risorse:");
        // se non ci sono rilevazioni, stampa "(nessuna)" e ritorna
        if (tutte.isEmpty()) {                       
            System.out.println("(nessuna)");
            return;
        }
        // altrimenti, per ogni rilevazione, stampa il nome della rilevazione e i nodi che la possiedono
        for (Map.Entry<String, List<String>> e : tutte.entrySet()) {
            // formato: "- R0: peer0, peer1"
            System.out.println("- " + e.getKey() + ": " + String.join(", ", e.getValue()));
        }
    }

    // Comando "log": stampa lo storico dei download registrati.
    private static void stampaLog(RegistroDownload registro) {
        // si ottiene la lista delle voci del registro dei download e le stampa in formato leggibile
        System.out.println("Risorse scaricate:");
        List<RegistroDownload.Entry> voci = registro.getEntries();
        // se non ci sono voci, stampa "(nessuna)" e ritorna
        if (voci.isEmpty()) {                           
            System.out.println("(nessuna)");
            return;
        }
        // altrimenti, per ogni voce, stampa la data, il nodo e la rilevazione scaricata
        for (RegistroDownload.Entry  v : voci) System.out.println(v);
    }
}
