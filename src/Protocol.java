/*
 * Questa classe raccoglie tutte le parole chiave del protocollo testuale usato per
 * far comunicare tra loro Client e Aggregatore, e i Client tra di loro (scambio
 * peer-to-peer).
 *
 * Ogni messaggio scambiato e' una riga di testo in UTF-8 che finisce
 * con un a-capo; le varie parti del messaggio (comando e argomenti) sono separate
 * da spazi. Il contenuto delle rilevazioni viene sempre mandato codificato in
 * Base64.
 *
 * Messaggi che il nodo invia all'aggregatore:
 *  - REGISTER <host> <porta> <r1,r2,...>  registra il nodo con le rilevazioni possedute ("-" se nessuna)
 *  - ADD <rilevazione>                    notifica una nuova rilevazione del nodo
 *  - LIST                                 chiede tutte le rilevazioni della rete con i nodi che le possiedono
 *  - NODES                                chiede l'elenco degli altri nodi sensore attivi
 *  - WHOHAS <rilevazione>                 chiede quali nodi possiedono una determinata rilevazione
 *  - DOWNLOAD <rilevazione>               chiede il token di accesso e il nodo da cui scaricare
 *  - RETRY <token> <peerId>               il nodo proposto non ha fornito la rilevazione, ne chiede un altro
 *  - DONE <token> <peerId> <rilevazione>  download riuscito, rilascia il token di accesso
 *  - DISCONNECT                           il nodo lascia la rete
 *
 * Risposte dell'aggregatore: OK, PEER <token> <peerId> <host> <porta>, UNAVAILABLE, ERR <motivo>;
 * gli elenchi (LIST, NODES, WHOHAS) sono una riga per elemento chiusa da END.
 *
 * Messaggi tra nodi: GET <rilevazione>, a cui il nodo risponde OK <contenuto Base64> oppure NOTFOUND.
 */
public final class Protocol {

    private Protocol() { }

    public static final String REGISTER   = "REGISTER";
    public static final String ADD        = "ADD";
    public static final String LIST       = "LIST";
    public static final String NODES      = "NODES";
    public static final String WHOHAS     = "WHOHAS";
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
