/**
 * Definizione centralizzata del protocollo testuale usato sia tra Client e
 * Aggregatore, sia tra due Client (scambio peer-to-peer).
 *
 * Ogni messaggio e' una riga di testo UTF-8 terminata da newline.
 * I token sono separati da spazi. Il contenuto delle rilevazioni viaggia
 * sempre codificato in Base64, cosi' da non contenere spazi o newline.
 *
 * OWNER: condiviso — da definire INSIEME nella sessione iniziale.
 */
public final class Protocol {

    private Protocol() { }

    // ----- Client -> Aggregatore -----
    public static final String REGISTER   = "REGISTER";   // REGISTER <host> <port> <res1,res2,...|->
    public static final String ADD        = "ADD";        // ADD <res>
    public static final String LIST       = "LIST";       // elenco rilevazioni remote
    public static final String NODES      = "NODES";      // elenco nodi attivi
    public static final String DOWNLOAD   = "DOWNLOAD";   // DOWNLOAD <res>
    public static final String RETRY      = "RETRY";      // RETRY <token> <failedPeerId>
    public static final String DONE       = "DONE";       // DONE <token> <fromPeerId> <res>
    public static final String DISCONNECT = "DISCONNECT"; // il nodo si sta sconnettendo

    // ----- Client (peer) -> Client (peer) -----
    public static final String GET = "GET";               // GET <res>

    // ----- Risposte -----
    public static final String OK          = "OK";
    public static final String PEER        = "PEER";        // PEER <token> <peerId> <host> <port>
    public static final String UNAVAILABLE = "UNAVAILABLE"; // rilevazione non reperibile sulla rete
    public static final String NOTFOUND    = "NOTFOUND";    // il peer non possiede piu' la rilevazione
    public static final String ERR         = "ERR";
    public static final String END         = ".";           // terminatore di elenco multi-riga
}
