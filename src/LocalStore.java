import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Gestisce la persistenza locale e la cache in memoria delle rilevazioni del nodo.
Ogni rilevazione e' un file di testo nella cartella storage/<nome_nodo>, il cui nome
e' il nome (univoco nel nodo) della rilevazione. Tutti i metodi pubblici sono
synchronized, perche' l'archivio viene usato contemporaneamente dalla console
(comandi add e download) e dai thread che servono le richieste degli altri nodi.
*/
public class LocalStore {

    private final File dir;
    private final Map<String, String> data = new HashMap<>();

    /*
    Inizializza la directory di storage per il nodo specifico e carica in memoria
    le rilevazioni preesistenti lette da disco. I file con un nome non utilizzabile come
    nome di rilevazione (vedi nomeValido) e i file nascosti vengono ignorati, segnalandolo.
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
                if (!f.isFile() || f.getName().startsWith(".")) {
                    continue;
                }
                if (!nomeValido(f.getName())) {
                    System.out.println("Attenzione: file '" + f.getName() + "' ignorato, nome di rilevazione non valido.");
                    continue;
                }
                data.put(f.getName(), readFile(f));
            }
        }
    }

    /*
    Controlla che un nome possa essere usato come nome di rilevazione.
    */
    public static boolean nomeValido(String rilevazione) {
        if (rilevazione == null || rilevazione.isEmpty()) return false;
        if (rilevazione.equals("-") || rilevazione.equals(".") || rilevazione.equals("..")) return false;
        for (char c : rilevazione.toCharArray()) {
            if (Character.isWhitespace(c) || c == ',' || c == '/' || c == '\\') return false;
        }
        return true;
    }

    /*
    Legge il contenuto testuale (UTF-8) di un file da disco ricostruendolo riga per riga.
    */
    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader lettore = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
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

    /*
    Restituisce una copia, ordinata per nome, dell'elenco dei nomi delle rilevazioni memorizzate.
    */
    public synchronized List<String> listNames() {
        List<String> nomi = new ArrayList<>(data.keySet());
        Collections.sort(nomi);
        return nomi;
    }

    /*
    Verifica la presenza di una specifica rilevazione nell'archivio locale.
    */
    public synchronized boolean has(String rilevazione) {
        if (rilevazione == null) return false;
        return data.containsKey(rilevazione);
    }

    /*
    Recupera il contenuto della rilevazione indicata, o null se non presente.
    */
    public synchronized String get(String rilevazione) {
        if (rilevazione == null) return null;
        return data.get(rilevazione);
    }

    /*
    Aggiunge una nuova rilevazione al nodo. Il nome viene prima validato (vedi nomeValido),
    altrimenti viene lanciata una IllegalArgumentException. Dato che il nome di una rilevazione
    e' univoco all'interno del nodo, se esiste gia' una rilevazione con lo stesso nome non viene
    sovrascritta e il metodo restituisce false. Altrimenti il contenuto viene scritto prima su
    file e solo dopo inserito nella mappa in memoria: se la scrittura fallisce la mappa non viene
    toccata, cosi' memoria e disco restano allineati. Essendo synchronized, il controllo e
    l'inserimento avvengono in modo atomico rispetto agli altri thread.
    */
    public synchronized boolean add(String rilevazione, String contenuto) throws IOException {
        if (!nomeValido(rilevazione)) {
            throw new IllegalArgumentException("nome della rilevazione non valido: '" + rilevazione
                    + "' (non sono ammessi spazi, virgole, '/', '\\', '-', '.' e '..')");
        }
        if (contenuto == null) {
            throw new IllegalArgumentException("Il contenuto della rilevazione non può essere nullo");
        }
        if (data.containsKey(rilevazione)) {
            return false;
        }

        File fileToSave = new File(dir, rilevazione);
        try (FileWriter writer = new FileWriter(fileToSave, StandardCharsets.UTF_8)) {
            writer.write(contenuto);
        }

        data.put(rilevazione, contenuto);
        return true;
    }
}
