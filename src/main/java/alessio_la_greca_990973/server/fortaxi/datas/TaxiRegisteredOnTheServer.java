package alessio_la_greca_990973.server.fortaxi.datas;

import alessio_la_greca_990973.server.forclient.TaxiStatistic;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.HashMap;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiRegisteredOnTheServer {
    //data structure used to represent the set of taxis currently
    //registered on the server, and so that are present in the
    //smart city.
    @XmlElement(name = "taxis")
    private HashMap<Integer, TaxiServerRepresentation> actualTaxis;

    @XmlElement(name = "statistics")
    private HashMap<Integer, ArrayList<TaxiStatistic>> taxiStatistics;    //TODO getter di vario tipo per il client

    private static TaxiRegisteredOnTheServer instance;
    private Object writeLock;       //static? no
    private int writersWaiting;     //static? no

    public TaxiRegisteredOnTheServer(){
        actualTaxis = new HashMap<Integer, TaxiServerRepresentation>();
        taxiStatistics = new HashMap<Integer, ArrayList<TaxiStatistic>>();
        writeLock = new Object();
        writersWaiting = 0;
    }
    public TaxiRegisteredOnTheServer(HashMap<Integer, TaxiServerRepresentation> actualTaxis, HashMap<Integer, ArrayList<TaxiStatistic>> taxiStatistics){
        this.actualTaxis = actualTaxis;
        this.taxiStatistics = taxiStatistics;
    }

    public synchronized static TaxiRegisteredOnTheServer getInstance(){
        if(instance==null){
            instance = new TaxiRegisteredOnTheServer();
        }
        return instance;
    }

    public boolean add(TaxiServerRepresentation t){
        synchronized (instance){
            int newID = t.getId();
            if(actualTaxis.containsKey(newID)){
                return false;
            }else{
                actualTaxis.put(newID, t);
                return true;
            }
        }
    }

    public boolean delete(int id) {
        synchronized (instance){
            if(actualTaxis.containsKey(id)){
                actualTaxis.remove(id);
                return true;
            }else{
                return false;
            }
        }
    }

    public HashMap<Integer, TaxiServerRepresentation> getActualTaxis(){
        return actualTaxis;
    }

    public void setActualTaxis(HashMap<Integer, TaxiServerRepresentation> actualTaxis) {
        this.actualTaxis = actualTaxis;
    }
}
