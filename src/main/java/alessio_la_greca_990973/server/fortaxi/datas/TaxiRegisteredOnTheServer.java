package alessio_la_greca_990973.server.fortaxi.datas;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.forclient.TaxiStatistic;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiRegisteredOnTheServer {

    private final boolean DEBUG_LOCAL = true;

    //data structure used to represent the set of taxis currently
    //registered on the server, and so that are present in the
    //smart city.
    @XmlElement(name = "taxis")
    private HashMap<Integer, TaxiServerRepresentation> actualTaxis;

    @XmlElement(name = "statistics")
    private HashMap<Integer, ArrayList<TaxiStatistic>> taxiStatistics;    //TODO getter di vario tipo per il client

    private static TaxiRegisteredOnTheServer instance;

    //variables for synchronizations purposes on the data structure actualTaxis
    private int writersWaiting;     //represents the number of writers waiting to write AND that are actually writing
    private int readersReading;     //represents the number of readers that are reading, not waiting to read!
    private boolean writerActive;   //represents whether or not a writer is actively writing on the shared data structure

    public TaxiRegisteredOnTheServer(){
        actualTaxis = new HashMap<Integer, TaxiServerRepresentation>();
        taxiStatistics = new HashMap<Integer, ArrayList<TaxiStatistic>>();
        writersWaiting = 0;
        readersReading = 0;
        writerActive = false;
    }
    public TaxiRegisteredOnTheServer(HashMap<Integer, TaxiServerRepresentation> actualTaxis, HashMap<Integer, ArrayList<TaxiStatistic>> taxiStatistics){
        this.actualTaxis = actualTaxis;
        this.taxiStatistics = taxiStatistics;
        writersWaiting = 0;
        readersReading = 0;
        writerActive = false;
    }

    public synchronized static TaxiRegisteredOnTheServer getInstance(){
        if(instance==null){
            instance = new TaxiRegisteredOnTheServer();
        }
        return instance;
    }

    public boolean add(TaxiServerRepresentation t){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("1");
        synchronized (instance){
            writersWaiting++;
            //a writer can write only if there are no readers currently reading OR there isn't another writer writing
            //in that moment
            if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("writerActive = " + writerActive + ", readersReading = " + readersReading);
            while(writerActive == true || readersReading > 0){
                try {
                    if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("writerActive = " + writerActive + ", readersReading = " + readersReading);
                    if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("2");
                    instance.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }
            writerActive = true;
        }
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("3");

        //at this point, I'm sure that AT MOST one writer is entering this section, and while it is doing so,
        //there are NO readers reading this data structure.
        boolean ret;
        int newID = t.getId();
        if(actualTaxis.containsKey(newID)){
            ret = false;
        }else{
            actualTaxis.put(newID, t);
            ret = true;
        }
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("4");
        synchronized (instance){
            writersWaiting--;
            writerActive = false;
            instance.notifyAll();   //if there is another writer, it will wake up and the readers will go back to sleep.
            //otherwise, the readers will start reading
        }
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("5");
        return ret;
    }

    public boolean delete(int id) {
        synchronized (instance){
            writersWaiting++;
            //a writer can write only if there are no readers currently reading OR there isn't another writer writing
            //in that moment
            while(writerActive = true || readersReading > 0){
                try {
                    instance.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }
            writersWaiting++;
            writerActive = true;
        }

        boolean ret;
        if(actualTaxis.containsKey(id)){
            actualTaxis.remove(id);
            ret = true;
        }else{
            ret = false;
        }


        synchronized (instance){
            writersWaiting--;
            writerActive = false;
            instance.notifyAll();   //if there is another writer, it will wake up and the readers will go back to sleep.
            //otherwise, the readers will start reading
        }
        return ret;
    }

    //the Administrator Clients will use this method to read the lsit of taxis currently present in the smart city,
    //so it need to implements the "Readers" logic so that, when there is a Writer waiting, the readers must
    //give priority to him, otherwise, multiple readers can read at the same time.
    public ArrayList<TaxiServerRepresentation> getActualTaxis(){
        //let's synchronize the access of the readers
        synchronized (instance){
            while(writersWaiting > 0){
                try {
                    instance.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }
            //only when the reader is sure that there are no writers waiting, it can start reading by adding 1 to
            //the readersReading variable to specify "ok, now I'm actively reading!"
            readersReading++;
        }

        //let's build a list of all the taxis present at the moment on the server
        ArrayList<TaxiServerRepresentation> ret = new ArrayList<TaxiServerRepresentation>();
        Set entrySet = actualTaxis.entrySet();
        Iterator it = entrySet.iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            ret.add((TaxiServerRepresentation) entry.getValue());
        }

        //now, since the reader has finished what he had to do, he can specify that it is done reading.
        synchronized (instance){
            readersReading--;
            notifyAll();
        }

        return ret;
    }


    //unused probably
    public void setActualTaxis(HashMap<Integer, TaxiServerRepresentation> actualTaxis) {
        this.actualTaxis = actualTaxis;
    }
}
