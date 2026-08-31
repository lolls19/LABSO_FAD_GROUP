import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Questa classe tiene lo storico di tutti i download avvenuti nella rete, sia
 * quelli riusciti sia quelli falliti, cosi' che l'aggregatore possa stamparlo con
 * il comando "log". Ogni volta che una sessione di download si chiude (con
 * successo o con un fallimento definitivo), GestoreNodo registra qui una nuova
 * voce con orario, rilevazione, nodo sorgente, nodo destinatario ed esito.
 *
 * Puo' essere usata da piu' thread contemporaneamente (un thread per ogni nodo
 * collegato), quindi sia la scrittura di una nuova voce sia la lettura di tutto
 * lo storico sono protette con synchronized.
 */
public class RegistroDownload {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    /*
     * Rappresenta una singola voce dello storico: e' immutabile (tutti i campi
     * sono final) perche' una volta registrato un evento non ha senso che cambi.
     */
    public static class Entry {

        private final LocalTime time;
        private final String rilevazione;
        private final String nodoSorgente;
        private final String nodoDestinatario;
        private final boolean esitoDownload;

        // Costruttore: salva tutti i dati dell'evento cosi' come vengono passati.
        public Entry(LocalTime time, String rilevazione, String nSorgente, String nDestinatario, boolean esito) {
            this.time = time;
            this.rilevazione = rilevazione;
            this.nodoSorgente = nSorgente;
            this.nodoDestinatario = nDestinatario;
            this.esitoDownload = esito;
        }

        // Semplici getter per leggere i campi della voce dall'esterno.
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

        // Trasforma la voce nella riga di testo che viene stampata dal comando "log": orario,
        // rilevazione, nodo sorgente e destinatario, con in piu' l'etichetta "(Download fallito)"
        // se l'esito non e' stato positivo.
        @Override
        public String toString() {
            String esito = esitoDownload ? "" : " (Download fallito)";

            return String.format("- %s %s da: %s a: %s%s",
                    time.format(formatter), rilevazione, nodoSorgente, nodoDestinatario, esito);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    // Aggiunge una nuova voce allo storico, prendendo l'orario attuale nel momento esatto in cui
    // viene chiamato. E' synchronized perche' thread diversi potrebbero chiamarlo nello stesso
    // istante, e senza protezione due aggiunte contemporanee potrebbero corrompere la lista.
    public synchronized void registra(String rilevazione, String nodoSorgente, String nodoDestinatario, boolean esito) {
        entries.add(new Entry(LocalTime.now(), rilevazione, nodoSorgente, nodoDestinatario, esito));
    }

    // Restituisce tutte le voci registrate finora, usato dal comando "log". E' synchronized per lo
    // stesso motivo di registra(), e restituisce una copia avvolta in una lista non modificabile:
    // cosi' chi la riceve non vede eventuali voci aggiunte dopo e soprattutto non puo' alterare
    // per errore lo storico originale.
    public synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
