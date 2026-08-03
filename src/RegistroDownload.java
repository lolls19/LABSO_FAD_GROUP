
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegistroDownload {
    // Un solo formattatore condiviso: mostra ore e minuti 

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    /*classe che rappresenta una singola voce del registro, immutabile e con campi final.
    contiene orario, rilevazione, nodo sorgente, nodo destinatario ed esito del download.
     */
    public static class Entry {

        private final LocalTime time;    // orario dell'evento
        private final String rilevazione;   // nome della rilevazione
        private final String nodoSorgente;       // nodo sorgente (da cui si scarica)
        private final String nodoDestinatario;         // nodo destinatario (che scarica)
        private final boolean esitoDownload;   // esito del download

        // Il costruttore riceve i dati e li salva nei campi.
        public Entry(LocalTime time, String rilevazione, String nSorgente, String nDestinatario, boolean esito) {
            this.time = time;
            this.rilevazione = rilevazione;
            this.nodoSorgente = nSorgente;
            this.nodoDestinatario = nDestinatario;
            this.esitoDownload = esito;
        }

        // Getter per i campi, non serve synchronized perché sono final e immutabili
        public LocalTime getTime() {
            return time;
        }

        public String getRilevazione() {
            return rilevazione;
        }

        public String getNodoSorgente() {
            return nodoSorgente;
        }

        public String getNodoDestinatario() {
            return nodoDestinatario;
        }

        public boolean isEsitoDownload() {
            return esitoDownload;
        }

        // Come la voce viene trasformata in testo per il comando "log".
        @Override
        //nel caso di download fallito, aggiunge l'etichetta "(Download fallito)" alla stringa finale
        //altrimenti lascia la stringa vuota.
        //successivamente compone la riga con orario formattato, rilevazione, sorgente, destinatario ed esito.

        public String toString() {
            String esito = esitoDownload ? "" : " (Download fallito)";

            return String.format("- %s %s da: %s a: %s%s",
                    time.format(formatter), rilevazione, nodoSorgente, nodoDestinatario, esito);
        }
    }
    // La lista che conserva tutte le voci, in ordine di inserimento.
    // l'unico modo di utilizzarla e' tramite i metodi sotto
    // che sono synchronized.
    private final List<Entry> entries = new ArrayList<>();

     /*
     * Registra un nuovo evento di download.
     * Chiamato dai ClientHandler (thread diversi) quando un download termina.
     *
     * E' 'synchronized' perche' piu' thread potrebbero chiamarlo insieme:
     * senza sincronizzazione, due aggiunte simultanee alla stessa ArrayList
     * potrebbero corromperla. Il lock garantisce che una scrittura per volta
     * modifichi la lista.
     *
     * Nota: l'orario viene preso qui con LocalTime.now(), cioe' nel momento
     * esatto della registrazione.
     */
    public synchronized void registra(String rilevazione, String nodoSorgente, String nodoDestinatario, boolean esito) {
        entries.add(new Entry(LocalTime.now(), rilevazione, nodoSorgente, nodoDestinatario, esito));
    }

    /*
         Restituisce l'elenco delle voci per il comando "log".
            E' 'synchronized' perche' piu' thread potrebbero chiamarlo insieme.
            Senza sincronizzazione, due letture simultanee della stessa ArrayList
            potrebbero restituire dati incoerenti. Il lock garantisce che una lettura per volta
            legga la lista.
            restituisce una copia della lista interna, avvolta in una lista di sola lettura.
            In questo modo, chi riceve la lista non vede le voci aggiunte dopo, e soprattutto non puo' modificarla. Restituire direttamente 'entries' esporrebbe il cuore della
            classe a modifiche esterne. 
            inoltre, Collections.unmodifiableList() avvolge la copia in una lista di 
            sola lettura: se qualcuno provasse ad aggiungere o rimuovere, otterrebbe
            un errore, doppia protezione.
     */
    public synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}

