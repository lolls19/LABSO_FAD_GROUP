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
 * Gestisce la persistenza locale e la cache in memoria delle rilevazioni del nodo.
 * Mantiene disallineamenti nulli tra File System e memoria interna, garantendo l'accesso 
 * thread-safe sincrono per le operazioni di lettura e scrittura concorrenti.
 */
public class LocalStore {

    private final File dir;
    private final Map<String, String> data = new HashMap<>();

    /**
     * Inizializza la directory di storage per il nodo specifico e carica in memoria 
     * le rilevazioni preesistenti lette da disco.
     */
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

    /**
     * Legge il contenuto testuale di un file da disco ricostruendolo riga per riga.
     */
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

    /**
     * Restituisce una copia thread-safe dell'elenco dei nomi delle rilevazioni memorizzate.
     */
    public synchronized List<String> listNames() {
        return new ArrayList<>(data.keySet());
    }

    /**
     * Verifica la presenza di una specifica rilevazione nell'archivio locale.
     */
    public synchronized boolean has(String rilevazione) {
        if (rilevazione == null) return false;
        return data.containsKey(rilevazione);
    }

    /**
     * Recupera il contenuto della rilevazione indicata, o null se non presente.
     */
    public synchronized String get(String rilevazione) {
        if (rilevazione == null) return null;
        return data.get(rilevazione);
    }

    /**
     * Valida il nome contro attacchi di Path Traversal, salva la rilevazione su file system 
     * e aggiorna la mappa in memoria in modo atomico rispetto ad altri thread.
     */
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
