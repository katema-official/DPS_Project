package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;

import java.util.ArrayList;
import java.util.Map;

public class BatteryManager implements Runnable{
    private Taxi thisTaxi;
    private int acks;
    private int currentParticipants;
    public Object updateAcks;
    public Object canRecharge;

    private ArrayList<Integer> pendingRequests;

    public BatteryManager(Taxi t){
        thisTaxi = t;
        acks = 0;
        updateAcks = new Object();
        canRecharge = new Object();

        //for correctly handling the recharge request of the current district, this taxi needs also to be always
        //ready to reply to another taxi that asks him the permission to go recharge. To do this, we create another
        //thread that simply listens to other taxis' recharge requests.
        BatteryListener bl = new BatteryListener(thisTaxi, this);
        Thread t3 = new Thread(bl);
        t3.start();
    }

    public void run(){
        while(true){		//!taxiMustTerminate
            synchronized(thisTaxi.alertBatteryRecharge){
                try {
                    thisTaxi.alertBatteryRecharge.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
                //will wake up when the battery is below 30% OR when an explicit request of recharge is given
            }

            if(true){       //TODO: !taxiMustTerminate
                acks = 0;
                synchronized (thisTaxi.otherTaxisLock){
                    for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : thisTaxi.getOtherTaxis().entrySet()){
                        //for each taxi that is in my district
                        if(SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY()) ==
                                SmartCity.getDistrict(entry.getValue().getCurrX(), entry.getValue().getCurrY())){
                            //I sum 1 to the number of participants from which I expect an ok message
                            currentParticipants++;
                            //and I also launch a new thread that will ask that taxi the permission for accessing
                            //the recharge station
                            Thread t = new Thread(new BatteryRequest(thisTaxi, this, entry.getValue()));
                            t.start();
                        }
                    }
                }

                //prendi tutti i taxi nello stesso distretto lanciando più thread
                //chiama

                synchronized(canRecharge){
                    try {
                        canRecharge.wait();
                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }


            }

        }
    }


    public void addAck(){
        synchronized (updateAcks) {
            this.acks++;
            if(acks == currentParticipants){
                synchronized (canRecharge){
                    canRecharge.notify();
                }
            }
        }
    }

    public ArrayList<Integer> getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(ArrayList<Integer> pendingRequests) {
        this.pendingRequests = pendingRequests;
    }
}
