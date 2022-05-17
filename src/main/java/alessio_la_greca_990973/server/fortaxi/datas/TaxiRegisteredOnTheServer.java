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
import java.lang.Math;

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

        startTransactionOnTaxiStatisticSCRIBE();

        //at this point, I'm sure that AT MOST one writer is entering this section, and while it is doing so,
        //there are NO readers reading this data structure.
        //------This code is synchronized thanks to the first lock, startTransactionOnTaxiRepresentationWRITER()-------
        boolean ret;
        int newID = t.getId();
        if(actualTaxis.containsKey(newID)){
            ret = false;
        }else{
            actualTaxis.put(newID, t);
            ret = true;
        }
        //-------------------------------------------------------------------------------------------------------------

        //--------This code is synchronized thanks to the second lock, startTransactionOnTaxiStatisticSCRIBE()---------
        if(ret == true) {
            //we are creating a new taxi, so we have to create also some support data structures.
            taxiStatistics.put(t.getId(), new ArrayList<>());
            readersReadingTaxiStatistics.put(t.getId(), 0);
            writersWaitingTaxiStatistics.put(t.getId(), 0);
            writerActiveTaxiStatistics.put(t.getId(), false);
            if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("Taxi " + t.getId() + " joined the city");
        }
        //-------------------------------------------------------------------------------------------------------------

        endTransactionOnTaxiStatisticSCRIBE();

        endTransactionOnTaxiRepresentationWRITER();
        return ret;
    }

    //FOR THE DELETE OPERATION THAT REMOVES A TAXI FROM THE SMART CITY
    public boolean delete(int id) {
        startTransactionOnTaxiRepresentationWRITER();

        startTransactionOnTaxiStatisticSCRIBE();

        //------This code is synchronized thanks to the first lock, startTransactionOnTaxiRepresentationWRITER()-------
        boolean ret;
        if(actualTaxis.containsKey(id)){
            actualTaxis.remove(id);
            ret = true;
        }else{
            ret = false;
        }
        //-------------------------------------------------------------------------------------------------------------

        //--------This code is synchronized thanks to the second lock, startTransactionOnTaxiStatisticSCRIBE()---------
        if(ret == true) {
            //we are deleting a taxi, so we have to remove all of its data, also the ones of the support data structures.
            taxiStatistics.remove(id);
            readersReadingTaxiStatistics.remove(id);
            writersWaitingTaxiStatistics.remove(id);
            writerActiveTaxiStatistics.remove(id);
            if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("Taxi " + id + " left the city");
        }
        //-------------------------------------------------------------------------------------------------------------

        endTransactionOnTaxiStatisticSCRIBE();

        endTransactionOnTaxiRepresentationWRITER();
        return ret;
    }

    //FOR THE POST OPERATION THAT ADDS A NEW TAXI STATISTIC (WITH TIMESTAMP) TO THE SERVER
    public boolean append(TaxiStatisticsPacket packet){
        //So, if done properly, we can allow a fine-grained synchronization. In fact:
        //1) we could synchronize every operation performed on "taxiStatistic" so that it can be accessed by at most
        //one thread, both when it needs to be written (new statistic from a taxi) and when it needs to be read
        //(from an administrator client). But this isn't very scalable. Instead, we could synchronize the access
        //to the statistics of one taxi by synchronizing on that taxi. In this way, multiple readers can read
        //the same values of the same taxi, but only one writer can write on it.
        int id = packet.getTaxiId();

        //allow = true means that the taxi still exists
        boolean allow = startTransactionOnTaxiStatisticsWRITER(id);
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("allowed to put data in taxi " + id + "? " + allow);
        if(allow) {
            TaxiStatisticWithTimestamp stat = new TaxiStatisticWithTimestamp(packet.getKilometers(), packet.getRides(),
                    packet.getPollutionAverages(), packet.getBatteryLevel(), packet.getTimestamp());
            taxiStatistics.get(id).add(stat);

            endTransactionOnTaxiStatisticsWRITER(id);

            return true;    //the append was successful
        }else{
            return false;
        }
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
    public TaxiStatistic getLastNStatisticOfTaxi(int id, int n){
        if(n == 0) return null;

        boolean allow = startTransactionOnTaxiStatisticsREADER(id);

        if(allow){
            ArrayList<TaxiStatisticWithTimestamp> selectedArray =
                    new ArrayList<TaxiStatisticWithTimestamp>(taxiStatistics.get(id));

            endTransactionOnTaxiStatisticsREADER(id);

            //faccio la media delle statistiche e poi la restituisco
            TaxiStatistic average = new TaxiStatistic();
            int sumKilometers = 0;
            int sumRides = 0;
            ArrayList<Double> pollutionsTotal = new ArrayList<>();
            int sumBattery = 0;

            int len = selectedArray.size();
            n = n < len ? n : len;
            for(int offset = len - n; offset < len; offset++){
                TaxiStatisticWithTimestamp current = selectedArray.get(offset);
                sumKilometers += current.getKilometers();
                sumRides += current.getRides();
                pollutionsTotal.addAll(current.getPollutionAverages());
                sumBattery += current.getBatteryLevel();
            }

            average.setKilometers(sumKilometers / n);
            average.setRides(sumRides / n);
            double totalSumPollutions = 0;
            for(Double d : pollutionsTotal){
                totalSumPollutions += d;
            }
            average.setPollutionAverage(totalSumPollutions / (double) pollutionsTotal.size());
            average.setBatteryLevel(sumBattery / n);

            return average;
        }

        return null;    //the taxi doesn't exist anymore.
    }

    public TaxiStatistic getGlobalStatisticsBetweenTimestamps(double t1, double t2){

        TaxiStatistic result = new TaxiStatistic();
        ArrayList<TaxiStatistic> averagesList = new ArrayList<>();
        for (int id : actualTaxis.keySet()){
            boolean allow = startTransactionOnTaxiStatisticsREADER(id);
            if(allow){

                ArrayList<TaxiStatisticWithTimestamp> current =
                        new ArrayList<TaxiStatisticWithTimestamp>(taxiStatistics.get(id));

                endTransactionOnTaxiStatisticsREADER(id);
                //take all the timestamps bw t1 and t2 with binary search, average them, and produce the result.
                int t1_index = binarySearch(current, t1);

                TaxiStatistic average = new TaxiStatistic();
                int sumKilometers = 0;
                int sumRides = 0;
                ArrayList<Double> pollutionsTotal = new ArrayList<>();
                int sumBattery = 0;

                int n = 0;

                while(t1_index < current.size() && current.get(t1_index).getTimestamp() <= t2){
                    sumKilometers += current.get(t1_index).getKilometers();
                    sumRides += current.get(t1_index).getRides();
                    pollutionsTotal.addAll(current.get(t1_index).getPollutionAverages());
                    sumBattery += current.get(t1_index).getBatteryLevel();

                    n++;
                    t1_index++;

                }

                if(n > 0) {     //if there were no statistics between t1 and t2, this code is skipped.
                    TaxiStatistic stat = new TaxiStatistic();
                    stat.setKilometers(sumKilometers / n);
                    stat.setRides(sumRides / n);
                    double totalSumPollutions = 0;
                    for (Double d : pollutionsTotal) {
                        totalSumPollutions += d;
                    }
                    stat.setPollutionAverage(totalSumPollutions / (double) pollutionsTotal.size());
                    stat.setBatteryLevel(sumBattery / n);

                    averagesList.add(stat);
                }

            }
        }

        int sumKilometers = 0;
        int sumRides = 0;
        double pollutionsTotal = 0D;
        int sumBattery = 0;
        for(TaxiStatistic ts : averagesList){
            sumKilometers += ts.getKilometers();
            sumRides += ts.getRides();
            pollutionsTotal += ts.getPollutionAverage();
            sumBattery += ts.getBatteryLevel();
        }

        if(averagesList.size() >= 1) {
            result.setKilometers(sumKilometers / averagesList.size());
            result.setRides(sumRides / averagesList.size());
            result.setPollutionAverage(pollutionsTotal / averagesList.size());
            result.setBatteryLevel(sumBattery / averagesList.size());
            return result;
        }
        return null;
    }


    //unused probably
    public void setActualTaxis(HashMap<Integer, TaxiServerRepresentation> actualTaxis) {
        this.actualTaxis = actualTaxis;
    }

    private int binarySearch(ArrayList<TaxiStatisticWithTimestamp> list, double t1){

        int max = list.size() - 1;
        int min = 0;

        while(max - min != 1){
            if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("min = " + min + ", max = " + max);
            int middle = min + (max - min)/2;
            double current_value = list.get(middle).getTimestamp();
            if(current_value >= t1){
                max = middle;
            }else{
                min = middle;
            }
        }

        if(list.get(min).getTimestamp() >= t1) return min;
        return max;

    }


    //*****************************************************************************************************************
    //*                                  Methods for synchronizing properly                                           *
    //*****************************************************************************************************************

    //-----------------------------------------------------------------------------------------------------------------
    //-----------These methods are used for proper synchronization on add(), remove() and getActualTaxis()-------------
    //--------------or, put in another way, for synchronization on the whole actualTaxis data structure----------------
    //-----------------------------------------------------------------------------------------------------------------

    //Coarse synchronization on actualTaxis.
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

    //Coarse synchronization on actualTaxis
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

    //-----------------------------------------------------------------------------------------------------------------
    //---------------These methods are used for proper synchronization on add(), remove(), append(),-------------------
    //-------------------getLastNStatisticOfTaxi() and getGlobalStatisticsBetweenTimestamps()--------------------------
    //-------------or, put in another way, for synchronization on the whole taxiStatistics data structure--------------
    //-----------------------------------------------------------------------------------------------------------------

    //Coarse synchronization on taxiStatistics
    private void startTransactionOnTaxiStatisticSCRIBE(){
        synchronized (globalLock){
            //-----------synchronization for writes/reads about the current taxi STATISTICS in the system--------------
            scribesWaiting++;
            int readers = 0;
            boolean isSomeoneWriting = false;
            boolean go = false;
            while(!go) {
                //if there is another scribe working, I can already suspend my work and wait for him to complete
                if(scribeActive == true){

                    try {
                        globalLock.wait();
                    } catch (InterruptedException e) {throw new RuntimeException(e);}

                }else {

                    for (TaxiServerRepresentation taxi : actualTaxis.values()) {    //I take the fine-grained locks one by one
                        synchronized (taxi) {
                            readers += readersReadingTaxiStatistics.get(taxi.getId());
                            isSomeoneWriting |= writerActiveTaxiStatistics.get(taxi.getId());
                            //TODO: you could break this for in the very moment readers > 0 || isSomeoneWriting == true
                        }
                    }
                    if (readers > 0 || isSomeoneWriting == true) {
                        try {
                            globalLock.wait();
                        } catch (InterruptedException e) {throw new RuntimeException(e);}
                    } else {
                        go = true;
                    }

                }
            }
            scribeActive = true;
            //---------------------------------------------------------------------------------------------------------
        }
    }

    private void endTransactionOnTaxiStatisticSCRIBE(){
        synchronized (globalLock){
            //-----------synchronization for writes/reads about the current taxi STATISTICS in the system--------------
            scribesWaiting--;
            scribeActive = false;
            if(scribesWaiting > 0){
                globalLock.notify();
            }else{
                for (TaxiServerRepresentation taxi : actualTaxis.values()) {
                    synchronized (taxi) {
                        taxi.notifyAll();
                    }
                }
            }
            //---------------------------------------------------------------------------------------------------------
        }
    }

    //fine synchronization on taxiStatistics (writers, or for the append() operation)
    //the return value is a boolean:
    //TRUE: the lock was acquired, a new data can be appended to the desired ArrayList
    //FALSE: the data structure was removed, you can't perform any operation on it
    private boolean startTransactionOnTaxiStatisticsWRITER(int id){
        //As a writer, I want exclusive access to the ArrayList of the statistics relative to the taxi with id "id".
        //but, if there is a scribe waiting, I must yield so that he can execute before me.
        boolean go = false;
        while(!go){

            //first, I check if there is a scribe waiting to write.
            synchronized (globalLock){
                go = true;
                if(scribesWaiting > 0){
                    go = false;
                }
            }

            try{
                TaxiServerRepresentation taxi = actualTaxis.get(id);
                if(taxi == null) return false;  //a scribe could have already deleted that taxi
                synchronized (taxi){
                    if(actualTaxis.containsKey(id)){
                        if(!go){
                            try{
                                taxi.wait(500); //the timeout is necessary because of the interleavings.
                                //it might happen that:
                                //1) the writer synchronizes on globalLock, sees that there is at least one scribe, and
                                //sets go to false.
                                //2) before the writer enters this if, the scribe performs his operations, and then,
                                //if it was the last one, he sends a notifyAll() on all taxis. But this writer didn't
                                //call wait() on this taxi.
                                //3) this writer calls wait() on this taxi, and waits for a notifyAll() that has already
                                //happened (so he would have to wait a new scribe to enter).
                                //
                                //using the wait, eventually the taxi will be awakened. I mean, in the worst case, the
                                //writer will wake up, find out that the scribe hasn't finished, and go back to sleep.
                            }catch(InterruptedException e){e.printStackTrace();}
                        }else{
                            //there were no scribes. So this writer can write? NO! He must be sure that there are no
                            //other writers or readers on this taxi.
                            int currNumberOfWaitingWriters = writersWaitingTaxiStatistics.get(id);
                            writersWaitingTaxiStatistics.replace(id, currNumberOfWaitingWriters + 1);
                            while(writerActiveTaxiStatistics.get(id) == true || readersReadingTaxiStatistics.get(id) > 0){
                                try {
                                    taxi.wait();
                                }catch(InterruptedException e){e.printStackTrace();}
                            }
                            writerActiveTaxiStatistics.put(id, true);
                            //now, and only now, the append can be executed! At this point, in fact, we are sure that
                            //we have let all the possible scribes pass and that there are no other writers or readers
                            //in this taxiStatistic's taxi
                        }

                    }else{
                        throw new NullPointerException();
                    }
                }
            }catch(NullPointerException e){
                return false;   //the scribe deleted this taxi, so no append operation can be done
            }

        }
        //this is executed only if go = true, that is, if there are no scribes AND (so) the mutual exclusion on this
        //taxi was granted.
        return true;
    }

    private void endTransactionOnTaxiStatisticsWRITER(int id){
        TaxiServerRepresentation taxi = actualTaxis.get(id);
        synchronized (taxi) {
            int tmp = writersWaitingTaxiStatistics.get(id);
            writersWaitingTaxiStatistics.put(id, tmp - 1);
            writerActiveTaxiStatistics.put(id, false);
            taxi.notifyAll();
        }
    }

    private boolean startTransactionOnTaxiStatisticsREADER(int id){
        boolean go = false;
        while(!go){

            //first, I check if there is a scribe waiting to write.
            synchronized (globalLock){
                go = true;
                if(scribesWaiting > 0){
                    go = false;
                }
            }

            try{
                TaxiServerRepresentation taxi = actualTaxis.get(id);
                if(taxi == null) return false;
                synchronized (taxi){
                    if(actualTaxis.containsKey(id)){
                        if(!go){
                            try{
                                taxi.wait(500);
                            }catch(InterruptedException e){e.printStackTrace();}
                        }else{
                            //there were no scribes. So this reader can read? NO! He must be sure that there are no
                            //writers on this taxi.
                            while(writersWaitingTaxiStatistics.get(id) > 0){
                                try {
                                    taxi.wait();
                                }catch(InterruptedException e){e.printStackTrace();}
                            }
                            int tmp = readersReadingTaxiStatistics.get(id);
                            readersReadingTaxiStatistics.put(id, tmp + 1);
                            //now, and only now, the append can be executed! At this point, in fact, we are sure that
                            //we have let all the possible scribes pass and that there are no other writers or readers
                            //in this taxiStatistic's taxi
                        }

                    }else{
                        throw new NullPointerException();
                    }
                }
            }catch(NullPointerException e){
                return false;   //the scribe deleted this taxi, so no append operation can be done
            }

        }
        //this is executed only if go = true, that is, if there are no scribes AND (so) the mutual exclusion on this
        //taxi was granted.
        return true;
    }

    private void endTransactionOnTaxiStatisticsREADER(int id){
        TaxiServerRepresentation taxi = actualTaxis.get(id);
        synchronized (taxi) {
            int tmp = readersReadingTaxiStatistics.get(id);
            readersReadingTaxiStatistics.put(id, tmp - 1);
            taxi.notifyAll();
        }
    }

}
