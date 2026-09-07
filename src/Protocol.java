/*
 * Questa classe raccoglie tutte le parole chiave del protocollo testuale usato per
 * far comunicare tra loro Client e Aggregatore, e i Client tra di loro (scambio
 * peer-to-peer).
 *
 * Ogni messaggio scambiato e' una riga di testo in UTF-8 che finisce
 * con un a-capo; le varie parti del messaggio (comando e argomenti) sono separate
 * da spazi. Il contenuto delle rilevazioni viene sempre mandato codificato in
 * Base64.
 */
public final class Protocol {

    private Protocol() { }

    public static final String REGISTER   = "REGISTER";
    public static final String ADD        = "ADD";
    public static final String LIST       = "LIST";
    public static final String DOWNLOAD   = "DOWNLOAD";
    public static final String RETRY      = "RETRY";
    public static final String DONE       = "DONE";
    public static final String DISCONNECT = "DISCONNECT";

    public static final String GET = "GET";

    public static final String OK          = "OK";
    public static final String PEER        = "PEER";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String NOTFOUND    = "NOTFOUND";
    public static final String ERR         = "ERR";
    public static final String END         = ".";
}
