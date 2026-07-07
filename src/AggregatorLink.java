import java.io.IOException;
import java.util.List;

/**
 * Unica connessione persistente del nodo sensore verso l'aggregatore.
 * Tutti i comandi che dialogano con l'aggregatore passano da qui.
 *
 * OWNER: Membro B.
 * I metodi requestDownload/retry/done sono richiamati dal Downloader (Membro C):
 * fanno parte del contratto condiviso e le loro firme non vanno cambiate senza
 * accordo.
 *
 * NOTA: SCHELETRO. Corpi da implementare da B. Ricordarsi che la connessione e'
 * condivisa: l'accesso al socket va serializzato (metodi synchronized).
 */
public class AggregatorLink {

    /** Apre la connessione all'aggregatore. Lancia IOException se irraggiungibile. */
    public AggregatorLink(String host, int port) throws IOException {
        // TODO (Membro B): aprire socket e stream.
        throw new UnsupportedOperationException("TODO: AggregatorLink(host, port)");
    }

    /** Identificativo assegnato dall'aggregatore dopo la registrazione. */
    public String getPeerId() {
        throw new UnsupportedOperationException("TODO: getPeerId()");
    }

    /** Indirizzo locale usato per raggiungere l'aggregatore, da annunciare ai peer. */
    public String localAddress() {
        throw new UnsupportedOperationException("TODO: localAddress()");
    }

    /** Registrazione all'avvio: comunica host/porta del PeerServer e le rilevazioni. */
    public String register(String peerHost, int peerPort, List<String> resources) throws IOException {
        throw new UnsupportedOperationException("TODO: register()");
    }

    /** Notifica l'aggiunta di una nuova rilevazione. */
    public void notifyAdd(String resource) throws IOException {
        throw new UnsupportedOperationException("TODO: notifyAdd()");
    }

    /** Elenco delle rilevazioni remote (una riga per rilevazione, "res peer1,peer2"). */
    public List<String> listRemote() throws IOException {
        throw new UnsupportedOperationException("TODO: listRemote()");
    }

    /** Comunica la disconnessione ordinata e chiude la connessione. */
    public void disconnect() throws IOException {
        throw new UnsupportedOperationException("TODO: disconnect()");
    }

    // --- Contratto per il Downloader (Membro C) ---

    /** Avvia la sessione di download: ritorna "PEER ..." oppure "UNAVAILABLE". */
    public String requestDownload(String resource) throws IOException {
        throw new UnsupportedOperationException("TODO: requestDownload()");
    }

    /** Segnala un tentativo fallito e chiede un altro nodo: "PEER ..." o "UNAVAILABLE". */
    public String retry(String token, String failedPeerId) throws IOException {
        throw new UnsupportedOperationException("TODO: retry()");
    }

    /** Conferma il download riuscito e rilascia il token. */
    public void done(String token, String fromPeerId, String resource) throws IOException {
        throw new UnsupportedOperationException("TODO: done()");
    }
}
