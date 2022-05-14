package alessio_la_greca_990973.server.fortaxi.datas;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.forclient.TaxiStatistic;
import alessio_la_greca_990973.server.forclient.TaxiStatisticWithTimestamp;
import alessio_la_greca_990973.server.fortaxi.datas.statistics.TaxiStatisticsPacket;

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
    private HashMap<Integer, ArrayList<TaxiStatisticWithTimestamp>> taxiStatistics;    //TODO getter di vario tipo per il client

    private static TaxiRegisteredOnTheServer instance;

    //********************************************************************************************************
    //*           variables for synchronizations purposes on the data structure actualTaxis                  *
    //********************************************************************************************************
    private int writersWaiting;     //represents the number of writers waiting to write AND that are actually writing
    private int readersReading;     //represents the number of readers that are reading, not waiting to read!
    private boolean writerActive;   //represents whether or not a writer is actively writing on the shared data structure

    //********************************************************************************************************
    //*           variables for synchronizations purposes on the data structure taxiStatistics               *
    //********************************************************************************************************
    private HashMap<Integer, Integer> writersWaitingTaxiStatistics;
    private HashMap<Integer, Integer> readersReadingTaxiStatistics;
    private HashMap<Integer, Boolean> writerActiveTaxiStatistics;
    private Object globalLock;
    private int scribesWaiting; //scribes can be intended as "threads that want to perform an add/remove operation of a taxi"
    private boolean scribeActive;

    public TaxiRegisteredOnTheServer(){
        actualTaxis = new HashMap<Integer, TaxiServerRepresentation>();
        taxiStatistics = new HashMap<Integer, ArrayList<TaxiStatisticWithTimestamp>>();
        writersWaiting = 0;
        readersReading = 0;
        writerActive = false;
        writersWaitingTaxiStatistics = new HashMap<>();
        readersReadingTaxiStatistics = new HashMap<>();
        writerActiveTaxiStatistics = new HashMap<>();
        globalLock = new Object();
        scribesWaiting = 0;
        scribeActive = false;
    }
    public TaxiRegisteredOnTheServer(HashMap<Integer, TaxiServerRepresentation> actualTaxis, HashMap<Integer, ArrayList<TaxiStatisticWithTimestamp>> taxiStatistics){
        this.actualTaxis = actualTaxis;
        this.taxiStatistics = taxiStatistics;
        writersWaiting = 0;
        readersReading = 0;
        writerActive = false;
        writersWaitingTaxiStatistics = new HashMap<>();
        readersReadingTaxiStatistics = new HashMap<>();
        writerActiveTaxiStatistics = new HashMap<>();
        globalLock = new Object();
        scribesWaiting = 0;
        scribeActive = false;
    }

    public synchronized static TaxiRegisteredOnTheServer getInstance(){
        if(instance==null){
            instance = new TaxiRegisteredOnTheServer();
        }
        return instance;
    }

    //FOR THE POST OPERATION THAT ADDS A NEW TAXI TO THE SMART CITY
    public boolean add(TaxiServerRepresentation t){
        startTransactionOnTaxiRepresentationWRITER();

        startTransactionOnTaxiStatisticWRITER();

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

        endTransactionOnTaxiStatisticWRITER();

        endTransactionOnTaxiRepresentationWRITER();
        return ret;
    }

    //FOR THE DELETE OPERATION THAT REMOVES A TAXI FROM THE SMART CITY
    public boolean delete(int id) {
        startTransactionOnTaxiRepresentationWRITER();

        startTransactionOnTaxiStatisticWRITER();

        boolean ret;
        if(actualTaxis.containsKey(id)){
            actualTaxis.remove(id);
            ret = true;
        }else{
            ret = false;
        }

        endTransactionOnTaxiStatisticWRITER();

        endTransactionOnTaxiRepresentationWRITER();
        return ret;
    }

    //FOR THE POST OPERATION THAT ADDS A NEW TAXI STATISTIC (WITH TIMESTAMP) TO THE SERVER
    public void append(TaxiStatisticsPacket packet){
        //So, if done properly, we can allow a fine-grained synchronization. In fact:
        //1) we could synchronize every operation performed on "taxiStatistic" so that it can be accessed by at most
        //one thread, both when it needs to be written (new statistic from a taxi) and when it needs to be read
        //(from an administrator client). But this isn't very scalable. Instead, we could synchronize the access
        //to the statistics of one taxi by synchronizing on that key-value. In this way, multiple readers can read
        //the same values of the same taxi, but only one writer can write on it.
        int id = packet.getTaxiId();        //TODO: guarda gli appunti degli scribi
        TaxiStatisticWithTimestamp stat = new TaxiStatisticWithTimestamp(packet.getKilometers(), packet.getRides(),
                packet.getPollutionAverages(), packet.getBatteryLevel(), packet.getTimestamp());
        taxiStatistics.get(id).add(stat);
    }

    //[this method is synchronized together with the add() and remove() methods listed above. In particular,
    //this is the reader, the other two are the writers]
    //------------------------------------------------------------------------------------------------------
    //the Administrator Clients will use this method to read the list of taxis currently present in the smart city,
    //so it need to implements the "Readers" logic so that, when there is a Writer waiting, the readers must
    //give priority to him, otherwise, multiple readers can read at the same time.
    public ArrayList<TaxiServerRepresentation> getActualTaxis(){
        //let's synchronize the access of the readers
        startTransactionOnTaxiRepresentationREADER();

        //TODO: serve anche un startTransactionOnTaxiStatisticREADER() ?

        //let's build a list of all the taxis present at the moment on the server
        ArrayList<TaxiServerRepresentation> ret = new ArrayList<TaxiServerRepresentation>();
        Set entrySet = actualTaxis.entrySet();
        Iterator it = entrySet.iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            ret.add((TaxiServerRepresentation) entry.getValue());
        }

        endTransactionOnTaxiRepresentationREADER();

        return ret;
    }

    //[this method is synchronized with the append() method above. In particular, this is a reader, the other one
    //is a writer]
    public TaxiStatistic getLastNStatisticOfTaxi(int id){
        ArrayList<TaxiStatisticWithTimestamp> selectedArray = taxiStatistics.get(id);


    }

    public TaxiStatistic getGlobalStatisticsBetweenTimestamps(long t1, long t2){

    }


    //unused probably
    public void setActualTaxis(HashMap<Integer, TaxiServerRepresentation> actualTaxis) {
        this.actualTaxis = actualTaxis;
    }




    //*****************************************************************************************************************
    //*                                  Methods for synchronizing properly                                           *
    //*****************************************************************************************************************

    private void startTransactionOnTaxiRepresentationWRITER(){
        synchronized (instance){
            //-------------------synchronization for writes/reads about the current taxis in the system----------------
            writersWaiting++;
            //a writer can write only if there are no readers currently reading OR there isn't another writer writing
            //in that moment
            while(writerActive == true || readersReading > 0){
                try {

                    instance.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }
            writerActive = true;
            //---------------------------------------------------------------------------------------------------------
        }
    }

    private void endTransactionOnTaxiRepresentationWRITER(){
        synchronized (instance){
            //-------------------synchronization for writes/reads about the current taxis in the system----------------
            writersWaiting--;
            writerActive = false;
            instance.notifyAll();   //if there is another writer, it will wake up and the readers will go back to sleep.
            //otherwise, the readers will start reading
            //---------------------------------------------------------------------------------------------------------
        }
    }

    private void startTransactionOnTaxiRepresentationREADER(){
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
    }

    private void endTransactionOnTaxiRepresentationREADER(){
        //now, since the reader has finished what he had to do, he can specify that it is done reading.
        synchronized (instance){
            readersReading--;
            instance.notifyAll();
        }
    }


    private void startTransactionOnTaxiStatisticWRITER(){
        synchronized (globalLock){
            //-----------synchronization for writes/reads about the current taxi STATISTICS in the system--------------
            scribesWaiting++;
            int readers = 0;
            boolean isSomeoneWriting = false;
            boolean go = false;
            while(!go) {
                for (TaxiServerRepresentation taxi : actualTaxis.values()) {
                    synchronized (taxi) {
                        readers += readersReadingTaxiStatistics.get(taxi.getId());
                        isSomeoneWriting |= writerActiveTaxiStatistics.get(taxi.getId());
                    }
                }
                if(scribeActive == true || readers > 0 || isSomeoneWriting == true){
                    try {
                        globalLock.wait();
                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }else{
                    go = true;
                }
            }
            scribeActive = true;
            //---------------------------------------------------------------------------------------------------------
        }
    }

    private void endTransactionOnTaxiStatisticWRITER(){
        synchronized (globalLock){
            //-----------synchronization for writes/reads about the current taxi STATISTICS in the system--------------
            scribesWaiting--;
            scribeActive = false;
            if(scribesWaiting > 0){
                globalLock.notify();
            }else{
                for (TaxiServerRepresentation taxi : actualTaxis.values()) {
                    synchronized (taxi) {
                        taxi.notify();
                    }
                }
            }
            //---------------------------------------------------------------------------------------------------------
        }
    }

}
