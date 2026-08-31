/*
 * Questa classe raccoglie tutte le parole chiave del protocollo testuale usato per
 * far comunicare tra loro Client e Aggregatore, e i Client tra di loro (scambio
 * peer-to-peer). Invece di scrivere le stringhe "a mano" in giro per il codice,
 * ogni comando e ogni risposta e' definito una sola volta qui come costante, cosi'
 * se un domani cambia il nome di un comando basta modificarlo in un punto solo.
 *
 * Ogni messaggio scambiato e' semplicemente una riga di testo in UTF-8 che finisce
 * con un a-capo; le varie parti del messaggio (comando e argomenti) sono separate
 * da spazi. Il contenuto delle rilevazioni viene sempre mandato codificato in
 * Base64 proprio per evitare che contenga spazi o a-capo che romperebbero il
 * formato a righe.
 */
public final class Protocol {

    // Costruttore privato: questa classe serve solo a contenere costanti, non ha senso crearne istanze.
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
