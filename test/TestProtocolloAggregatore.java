import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/*
 Client di prova che parla il protocollo grezzo con un aggregatore GIA' AVVIATO
 (avvialo prima in un altro terminale con: java -cp out Master <porta>).

 Simula due nodi fornitori e un nodo richiedente collegandosi direttamente con
 dei socket, senza passare da Client.java, cosi' da verificare in automatico le
 risposte dell'aggregatore (REGISTER, LIST, NODES, DOWNLOAD, RETRY, DONE,
 DISCONNECT, comando sconosciuto) senza terminali interattivi.
 */
public class TestProtocolloAggregatore {

    private static int totale = 0;
    private static int passati = 0;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: java TestProtocolloAggregatore <host> <porta>");
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

        // tre connessioni indipendenti: due fornitori e un richiedente, come farebbero tre nodi veri
        try (Sessione fornitore1 = new Sessione(host, port);
             Sessione fornitore2 = new Sessione(host, port);
             Sessione richiedente = new Sessione(host, port)) {

            // -- REGISTER: due fornitori con la stessa rilevazione, un richiedente senza rilevazioni --
            String peerF1 = registra(fornitore1, "127.0.0.1", 9001, "RTest");
            String peerF2 = registra(fornitore2, "127.0.0.1", 9002, "RTest");
            String peerR = registra(richiedente, "127.0.0.1", 9003, null);

            // -- LIST: RTest deve risultare posseduta da entrambi i fornitori --
            String rigaRTest = cercaRiga(richiedente.inviaMultiRiga(Protocol.LIST), "RTest");
            check("LIST mostra RTest posseduta da entrambi i fornitori",
                    rigaRTest != null && rigaRTest.contains(peerF1) && rigaRTest.contains(peerF2));

            // -- NODES: tutti e tre i nodi devono risultare attivi --
            List<String> nodi = richiedente.inviaMultiRiga(Protocol.NODES);
            check("NODES elenca tutti e tre i nodi registrati",
                    !nodi.isEmpty() && nodi.get(0).contains(peerF1)
                            && nodi.get(0).contains(peerF2) && nodi.get(0).contains(peerR));

            // -- DOWNLOAD: l'aggregatore deve proporre uno dei due fornitori --
            String[] primaProposta = campiPeer(richiedente.invia(Protocol.DOWNLOAD + " RTest"));
            check("DOWNLOAD risponde PEER con token, id, host e porta del fornitore", primaProposta != null);
            String token = primaProposta[1];
            String primoFornitore = primaProposta[2];
            check("il fornitore proposto e' uno dei due registrati",
                    primoFornitore.equals(peerF1) || primoFornitore.equals(peerF2));

            // -- RETRY: si simula il fallimento del primo fornitore, deve proporre l'altro --
            String[] secondaProposta = campiPeer(richiedente.invia(Protocol.RETRY + " " + token + " " + primoFornitore));
            check("RETRY risponde di nuovo PEER con lo stesso token",
                    secondaProposta != null && secondaProposta[1].equals(token));
            String secondoFornitore = secondaProposta[2];
            check("dopo RETRY l'aggregatore propone l'ALTRO fornitore, non quello escluso",
                    !secondoFornitore.equals(primoFornitore)
                            && (secondoFornitore.equals(peerF1) || secondoFornitore.equals(peerF2)));

            // -- DONE: chiude la sessione con successo --
            String rispostaDone = richiedente.invia(Protocol.DONE + " " + token + " " + secondoFornitore + " RTest");
            check("DONE conferma il download con OK", rispostaDone != null && rispostaDone.startsWith(Protocol.OK));

            // -- dopo DONE, il richiedente deve comparire tra i possessori di RTest --
            String rigaRTestDopoDone = cercaRiga(richiedente.inviaMultiRiga(Protocol.LIST), "RTest");
            check("dopo DONE, LIST mostra anche il richiedente tra i possessori di RTest",
                    rigaRTestDopoDone != null && rigaRTestDopoDone.contains(peerR));

            // -- DOWNLOAD di una rilevazione che nessuno possiede --
            String rispostaAssente = richiedente.invia(Protocol.DOWNLOAD + " Inesistente123");
            check("DOWNLOAD di una rilevazione inesistente risponde UNAVAILABLE",
                    rispostaAssente != null && rispostaAssente.startsWith(Protocol.UNAVAILABLE));

            // -- comando non previsto dal protocollo --
            String rispostaErrore = richiedente.invia("PIPPO");
            check("un comando sconosciuto risponde ERR", rispostaErrore != null && rispostaErrore.startsWith(Protocol.ERR));

            // -- DISCONNECT: il fornitore 1 si scollega correttamente --
            String rispostaDisconnect = fornitore1.invia(Protocol.DISCONNECT);
            check("DISCONNECT risponde OK", rispostaDisconnect != null && rispostaDisconnect.startsWith(Protocol.OK));
            fornitore1.close();

            // -- dopo il quit, il nodo non e' piu' tra quelli attivi... --
            List<String> nodiDopoQuit = richiedente.inviaMultiRiga(Protocol.NODES);
            check("dopo il DISCONNECT il nodo non compare piu' tra i nodi attivi",
                    !nodiDopoQuit.isEmpty() && !nodiDopoQuit.get(0).contains(peerF1));

            // -- ...ma resta ancora elencato come possessore di RTest --
            String rigaRTestDopoQuit = cercaRiga(richiedente.inviaMultiRiga(Protocol.LIST), "RTest");
            check("dopo il DISCONNECT, LIST continua a mostrare il nodo tra i possessori di RTest",
                    rigaRTestDopoQuit != null && rigaRTestDopoQuit.contains(peerF1));

            // -- messaggio inatteso mentre una sessione di download e' attiva (ne' DONE ne' RETRY) --
            try (Sessione fornitore3 = new Sessione(host, port)) {
                registra(fornitore3, "127.0.0.1", 9004, "RGarbage");
                String[] propostaGarbage = campiPeer(richiedente.invia(Protocol.DOWNLOAD + " RGarbage"));
                check("DOWNLOAD RGarbage risponde PEER (setup per il test del ramo ERR)", propostaGarbage != null);

                String rispostaGarbage = richiedente.invia("BOOM");
                check("un messaggio non atteso durante una sessione di download attiva risponde ERR",
                        rispostaGarbage != null && rispostaGarbage.startsWith(Protocol.ERR));

                // dopo l'ERR la sessione si chiude: il richiedente torna al ciclo comandi normale
                List<String> nodiDopoErr = richiedente.inviaMultiRiga(Protocol.NODES);
                check("dopo l'ERR nella sessione di download, la connessione torna al ciclo comandi normale",
                        !nodiDopoErr.isEmpty());
            }
        }

        System.out.println();
        System.out.println(passati + "/" + totale + " controlli superati.");
        if (passati != totale) {
            System.exit(1);
        }
    }

    // registra un nodo e restituisce l'id assegnato, controllando che la risposta sia OK
    private static String registra(Sessione s, String host, int port, String rilevazioni) throws IOException {
        String elenco = (rilevazioni == null) ? "-" : rilevazioni;
        String risposta = s.invia(Protocol.REGISTER + " " + host + " " + port + " " + elenco);
        check("REGISTER risponde OK con un id assegnato", risposta != null && risposta.startsWith(Protocol.OK));
        return risposta.split("\\s+")[1];
    }

    // cerca tra le righe di una LIST quella che inizia con la rilevazione cercata
    private static String cercaRiga(List<String> righe, String rilevazione) {
        for (String r : righe) {
            if (r.startsWith(rilevazione + " ")) return r;
        }
        return null;
    }

    // valida e spezza una risposta "PEER <token> <peerId> <host> <porta>"
    private static String[] campiPeer(String risposta) {
        if (risposta == null || !risposta.startsWith(Protocol.PEER + " ")) return null;
        String[] campi = risposta.split("\\s+");
        return (campi.length == 5) ? campi : null;
    }

    private static void check(String descrizione, boolean condizione) {
        totale++;
        if (condizione) {
            passati++;
            System.out.println("[OK] " + descrizione);
        } else {
            System.out.println("[FALLITO] " + descrizione);
        }
    }

    // una connessione testuale verso l'aggregatore, con lo stesso stile di GestoreNodo/AggregatorLink
    private static class Sessione implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader lettore;
        private final PrintWriter scrittore;

        Sessione(String host, int port) throws IOException {
            socket = new Socket(host, port);
            lettore = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            scrittore = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        // invia una riga e legge la riga di risposta singola
        String invia(String riga) throws IOException {
            scrittore.println(riga);
            return lettore.readLine();
        }

        // invia un comando la cui risposta e' multi-riga terminata da "." (LIST, NODES)
        List<String> inviaMultiRiga(String comando) throws IOException {
            scrittore.println(comando);
            List<String> righe = new ArrayList<>();
            String riga;
            while ((riga = lettore.readLine()) != null && !riga.equals(Protocol.END)) {
                righe.add(riga);
            }
            return righe;
        }

        @Override
        public void close() throws IOException {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }
}
