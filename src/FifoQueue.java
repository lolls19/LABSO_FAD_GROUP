/**
 * Lock equo (FIFO) realizzato con il meccanismo "prendi il biglietto": ogni
 * thread che richiede il lock riceve un numero progressivo e attende finche'
 * il turno corrente non coincide con il proprio numero.
 *
 * A differenza di un semplice synchronized, garantisce che le richieste vengano
 * servite nello stesso ordine in cui sono arrivate (niente sorpassi), cosi' che
 * nessun thread resti in attesa indefinita.
 */
public class FifoQueue{
    private int succ=0; // inizializzo il "prossimo numero" da assegnare al thread che richiede il lock (il "biglietto").
    private int turno=0; // inizializzo il numero del thread attualmente autorizzato ad accedere alla sezione critica.

    public synchronized void acquisisciLock(){ 
        // Assegno il numero di turno al thread corrente e incremento il contatore per il prossimo thread che arriverà.
        int numPersonale = succ++;
        while(numPersonale != turno){
        // Finché non è il proprio turno, il thread resta in attesa. Uso un ciclo while (e non un semplice if) per gestire
        // correttamente i risvegli spuri e la presenza di più thread in coda.
            try{
                wait();
            }

            catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }

        }
        // A questo punto numPersonale == turno: il thread può procedere ed entrare nella sezione critica
    }
    public synchronized void rilascioLock(){
        turno++;
        // Sveglio tutti i thread in attesa, perché non si può sapere a priori quale thread
        // in attesa abbia esattamente il numero corrispondente al nuovo turn
        notifyAll();
    }

}
