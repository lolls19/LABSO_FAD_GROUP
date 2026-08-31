import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Questa classe rappresenta l'archivio locale delle rilevazioni di un nodo
 * sensore: ogni rilevazione e' identificata da un nome univoco e ha un contenuto
 * testuale. I dati vengono tenuti in memoria (in una mappa) per un accesso
 * veloce, ma vengono anche salvati su disco dentro una cartella dedicata al
 * nodo, cosi' che se il nodo viene riavviato ritrova le rilevazioni che aveva
 * gia'. Tutti i metodi pubblici sono synchronized perche' questa classe viene
 * usata sia dalla console del nodo sia dal server P2P che risponde alle
 * richieste degli altri nodi, quindi puo' essere acceduta da piu' thread
 * contemporaneamente.
 */
public class LocalStore {

    private final File dir;
    private final Map<String, String> data = new HashMap<>();

    // Prepara l'archivio del nodo: crea (se non esiste gia') la cartella di storage dedicata a
    // questo nodo dentro "storage/<nomeNodo>", e carica in memoria tutte le rilevazioni gia'
    // presenti su disco da un'esecuzione precedente. Rifiuta un nome di nodo nullo o vuoto, perche'
    // non si potrebbe costruire un percorso valido.
    public LocalStore(String nodeName) throws IOException {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Il nome del nodo non può essere vuoto");
        }

        this.dir = new File("storage", nodeName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Impossibile creare la cartella di storage: " + dir.getAbsolutePath());
            }
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String contenuto = readFile(f);
                    data.put(f.getName(), contenuto);
                }
            }
        }
    }

    // Legge tutto il contenuto di un file riga per riga e lo ricompone in un'unica stringa,
    // rimettendo gli a-capo tra una riga e l'altra (tranne che dopo l'ultima), cosi' un contenuto
    // multi-riga viene restituito esattamente come era stato salvato.
    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader lettore = new BufferedReader(new FileReader(file))) {
            String riga;
            boolean first = true;
            while ((riga = lettore.readLine()) != null) {
                if (!first) {
                    sb.append("\n");
                }
                sb.append(riga);
                first = false;
            }
        }
        return sb.toString();
    }

    // Restituisce i nomi di tutte le rilevazioni possedute dal nodo.
    public synchronized List<String> listNames() {
        return new ArrayList<>(data.keySet());
    }

    // Dice se il nodo possiede la rilevazione indicata (false anche se il nome passato e' null).
    public synchronized boolean has(String rilevazione) {
        if (rilevazione == null) return false;
        return data.containsKey(rilevazione);
    }

    // Restituisce il contenuto testuale della rilevazione richiesta, o null se il nodo non la
    // possiede.
    public synchronized String get(String rilevazione) {
        if (rilevazione == null) return null;
        return data.get(rilevazione);
    }

    // Aggiunge una nuova rilevazione oppure aggiorna il contenuto di una gia' esistente, sia in
    // memoria sia su disco. Controlla prima che il nome non contenga caratteri pericolosi (come
    // "/", "\" o "..") che permetterebbero di scrivere fuori dalla cartella del nodo, e che il
    // contenuto non sia nullo.
    public synchronized void add(String rilevazione, String contenuto) throws IOException {
        if (rilevazione == null || rilevazione.contains("/") || rilevazione.contains("\\") || rilevazione.equals("..")) {
            throw new IllegalArgumentException("Nome della rilevazione non valido o non sicuro: " + rilevazione);
        }
        if (contenuto == null) {
            throw new IllegalArgumentException("Il contenuto della rilevazione non può essere nullo");
        }

        data.put(rilevazione, contenuto);

        File fileToSave = new File(dir, rilevazione);
        try (FileWriter writer = new FileWriter(fileToSave)) {
            writer.write(contenuto);
        }
    }
}
