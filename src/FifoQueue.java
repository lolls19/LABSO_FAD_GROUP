/*
Questa classe implementa un lock "equo", cioe' che rispetta l'ordine di
arrivo delle richieste (FIFO), usando la tecnica del "prendi il numero": ogni
thread che vuole entrare nella sezione critica riceve un biglietto con un
numero progressivo e resta in attesa finche' non arriva il suo turno. A
differenza di un semplice "synchronized", che non garantisce in che ordine i
thread in attesa vengano risvegliati, questa implementazione assicura che
nessuno "salti la fila" e che nessun thread resti in attesa per sempre.
*/
public class FifoQueue {
    private int succ = 0;
    private int turno = 0;

    /*
    Assegna al thread chiamante il prossimo numero di biglietto disponibile, poi lo mette in
    attesa finche' il turno corrente non coincide con il proprio numero. Il controllo e' dentro
    un while (invece di un semplice if) proprio per gestire correttamente sia i risvegli spuri
    sia il caso in cui, quando ci si risveglia, non sia ancora il proprio turno perche' in coda
    ci sono altri thread prima di noi.
    Il thread non puo' abbandonare la coda, altrimenti il suo biglietto non verrebbe mai
    rilasciato e tutti quelli dopo di lui resterebbero bloccati: per questo un'eventuale
    interruzione viene solo ricordata e ripristinata quando arriva il turno. Richiamare subito
    interrupt() dentro il ciclo farebbe lanciare di nuovo l'eccezione alla wait() successiva, e il
    thread girerebbe a vuoto senza mai fermarsi.
    */
    public synchronized void acquisisciLock() {
        int numPersonale = succ++;
        boolean interrotto = false;
        while (numPersonale != turno) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrotto = true;
            }
        }
        if (interrotto) {
            Thread.currentThread().interrupt();
        }
    }

    /*
    Fa avanzare il turno di uno e sveglia tutti i thread in attesa: ognuno ricontrollera' la
    propria condizione, ma solo quello il cui numero coincide con il nuovo turno potra' davvero
    proseguire, gli altri torneranno in attesa.
    */
    public synchronized void rilascioLock() {
        turno++;
        notifyAll();
    }
}
