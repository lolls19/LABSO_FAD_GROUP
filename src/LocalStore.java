import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Archivio locale delle rilevazioni di un nodo sensore.
 * Ogni rilevazione ha un nome univoco e un contenuto testuale.
 *
 * OWNER: Membro B. Usato in lettura da Membro C (PeerRequestHandler, Downloader).
 *
 * NOTA: questo e' lo SCHELETRO condiviso. Le firme dei metodi NON vanno cambiate
 * senza avvisare B e C, perche' sono il contratto tra i due moduli del sensore.
 * I corpi vanno implementati da B.
 */
public class LocalStore {

    /**
     * Prepara l'archivio del nodo (es. una cartella dedicata) e carica le
     * rilevazioni gia' presenti / pre-allocate.
     * @param nodeName nome del nodo, usato per la cartella di storage.
     */

    // Campi raggruppati all'inizio della classe
    private final File dir;
    private final Map<String, String> data = new HashMap<>();

    public LocalStore(String nodeName) throws IOException {
        // Validazione dell'input per evitare percorsi nulli o vuoti
        if (nodeName == null || nodeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Il nome del nodo non può essere vuoto");
        }

        this.dir = new File("storage", nodeName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Impossibile creare la cartella di storage: " + dir.getAbsolutePath());
            }
        }

        // Carica le rilevazioni già presenti
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

    /**
     * Metodo di supporto privato per leggere il file riga per riga.
     * Implementato con BufferedReader per rispettare le specifiche di B.
     */
    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append("\n");
                }
                sb.append(line);
                first = false;
            }
        }
        return sb.toString();
    }

    /** @return i nomi di tutte le rilevazioni possedute dal nodo. */
    public synchronized List<String> listNames() {
        return new ArrayList<>(data.keySet());
    }

    /** @return true se il nodo possiede la rilevazione indicata. */
    public synchronized boolean has(String rilevazione) {
        if (rilevazione == null) return false;
        return data.containsKey(rilevazione);
    }

    /** @return il contenuto testuale della rilevazione, o null se assente. */
    public synchronized String get(String rilevazione) {
        if (rilevazione == null) return null;
        return data.get(rilevazione);
    }

    /** Aggiunge o aggiorna una rilevazione, persistendola. */
    public synchronized void add(String rilevazione, String contenuto) throws IOException {
       // Protezione da attacchi di Directory Traversal (es. rilevazione = "../file.txt")
        if (rilevazione == null || rilevazione.contains("/") || rilevazione.contains("\\") || rilevazione.equals("..")) {
            throw new IllegalArgumentException("Nome della rilevazione non valido o non sicuro: " + rilevazione);
        }
        if (contenuto == null) {
            throw new IllegalArgumentException("Il contenuto della rilevazione non può essere nullo");
        }

       // 1. Aggiorna la struttura dati concorrente in memoria
        data.put(rilevazione, contenuto);

        // 2. Persiste il dato su disco (sovrascrive se già presente)
        File fileToSave = new File(dir, rilevazione);
        try (FileWriter writer = new FileWriter(fileToSave)) {
            writer.write(contenuto);
        }
    }
}