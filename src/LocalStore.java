import java.io.IOException;
import java.util.List;

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
    public LocalStore(String nodeName) throws IOException {
        // TODO (Membro B): creare la cartella e caricare le rilevazioni esistenti.
        throw new UnsupportedOperationException("TODO: LocalStore(nodeName)");
    }

    /** @return i nomi di tutte le rilevazioni possedute dal nodo. */
    public List<String> listNames() {
        throw new UnsupportedOperationException("TODO: listNames()");
    }

    /** @return true se il nodo possiede la rilevazione indicata. */
    public boolean has(String name) {
        throw new UnsupportedOperationException("TODO: has()");
    }

    /** @return il contenuto testuale della rilevazione, o null se assente. */
    public String get(String name) {
        throw new UnsupportedOperationException("TODO: get()");
    }

    /** Aggiunge o aggiorna una rilevazione, persistendola. */
    public void add(String name, String content) throws IOException {
        // TODO (Membro B): salvare in memoria e su disco.
        throw new UnsupportedOperationException("TODO: add()");
    }
}
