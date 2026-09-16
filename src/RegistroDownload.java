import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Questa classe tiene lo storico di tutte le richieste di download avvenute nella rete, sia
 * quelle riuscite sia quelle fallite, cosi' che l'aggregatore possa stamparlo su terminale con
 * il comando "log".
 * Il download viene richiesto da un nodo (destinatario) a un altro nodo (sorgente).
 *  Durante una sessione di download il GestoreNodo dell'aggregatore chiama il metodo registra() per
 *  ogni tentativo: quando il download riesce, quando il nodo proposto non fornisce la rilevazione
 *  (RETRY), quando la rilevazione non e' disponibile su nessun nodo e quando la sessione si interrompe.
 *  Ogni voce contiene l'orario, la rilevazione, il nodo sorgente e destinatario, e l'esito del download.
 *
 * Puo' essere usata da piu' thread contemporaneamente (un thread per ogni nodo
 * collegato), quindi sia la scrittura di una nuova voce sia la lettura di tutto
 * lo storico sono protette con synchronized.
 */
public class RegistroDownload {
    // Formatter per stampare l'orario in formato HH:mm
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    /*
     * La classe interna Entry rappresenta una singola voce dello storico:
     * contiene l'orario, la rilevazione, il nodo sorgente e destinatario, e l'esito del download.
     * E' immutabile, quindi non serve sincronizzarla.
     */
    public static class Entry {

        private final LocalTime time;
        private final String rilevazione;
        private final String nodoSorgente;
        private final String nodoDestinatario;
        private final boolean esitoDownload;

        // metodo costruttore che inizializza tutti i campi della voce con i valori passati come argomenti.
        public Entry(LocalTime time, String rilevazione, String nSorgente, String nDestinatario, boolean esito) {
            this.time = time;
            this.rilevazione = rilevazione;
            this.nodoSorgente = nSorgente;
            this.nodoDestinatario = nDestinatario;
            this.esitoDownload = esito;
        }

        //getter per leggere i campi della voce dall'esterno.
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

        // metodo toString() per stampare la voce nel formato "- HH:mm rilevazione da: sorgente a: destinatario".
        // Le richieste andate a buon fine vengono stampate esattamente in questo formato, mentre a quelle
        // fallite viene aggiunto " (fallito)", cosi' dal log si capisce se ogni richiesta e' andata a buon fine.
        // Se la rilevazione non era disponibile su nessun nodo, al posto del nodo sorgente compare "-".
        // questo metodo viene usato dal comando "log" per stampare tutte le voci dello storico.
        @Override
        public String toString() {
            String esito = esitoDownload ? "" : " (fallito)";

            return String.format("- %s %s da: %s a: %s%s",
                    time.format(formatter), rilevazione, nodoSorgente, nodoDestinatario, esito);
        }
    }
    // Lista di tutte le voci dello storico, inizialmente vuota, gli elementi sono  di tipo Entry.
    private final List<Entry> entries = new ArrayList<>();

    // Aggiunge una nuova voce allo storico, prendendo l'orario attuale nel momento esatto in cui
    // viene chiamato. E' synchronized perche' thread diversi potrebbero chiamarlo nello stesso
    // istante e si potrebbero creare problemi di concorrenza se due thread scrivono contemporaneamente sulla lista.
    public synchronized void registra(String rilevazione, String nodoSorgente, String nodoDestinatario, boolean esito) {
        entries.add(new Entry(LocalTime.now(), rilevazione, nodoSorgente, nodoDestinatario, esito));
    }

    // Restituisce una copia della lista di tutte le voci dello storico, in modo che chi la riceve
    // non possa modificarla e non rischi di alterare lo stato interno della classe.
    // la copia e' unmodifiable, quindi chi la riceve non puo' modificarla, ma solo leggerla.
    // E' synchronized perche' thread diversi potrebbero chiamarlo nello stesso istante e si
    //  potrebbero creare problemi di concorrenza se un thread legge la lista mentre un altro la sta modificando.
    public synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
