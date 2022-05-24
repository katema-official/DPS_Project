package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.PendingRechargeRequestQueue;
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
    public Object rechargeStateLock;

    private double timestampOfRequest;

    private int rechargeState;  //0 = not interested, 1 = interested, 2 = currently using the resource


    public BatteryManager(Taxi t){
        thisTaxi = t;
        acks = 0;
        updateAcks = new Object();
        canRecharge = new Object();

        rechargeState = 0;

        //for correctly handling the recharge request of the current district, this taxi needs also to be always
        //ready to reply to another taxi that asks him the permission to go recharge. To do this, we create another
        //thread that simply listens to other taxis' recharge requests.
        BatteryListener bl = new BatteryListener(thisTaxi, this);
        Thread th = new Thread(bl);
        th.start();
    }

    public void run(){
        while(true){		//!taxiMustTerminate
            synchronized(thisTaxi.alertBatteryRecharge){
                try {
                    thisTaxi.alertBatteryRecharge.wait();
                    synchronized (rechargeStateLock) {
                        rechargeState = 1;
                        timestampOfRequest = System.currentTimeMillis();
                    }
                } catch (InterruptedException e) {throw new RuntimeException(e);}
                //will wake up when the battery is below 30% OR when an explicit request of recharge is given
            }

            District currentDistrict = SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY());

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
                            Thread t = new Thread(new BatteryRequest(thisTaxi, this, entry.getValue(),
                                    timestampOfRequest, currentDistrict));
                            t.start();
                        }
                    }
                }

                synchronized(canRecharge){
                    try {
                        canRecharge.wait();
                        synchronized (rechargeStateLock) {
                            rechargeState = 2;
                        }
                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }

                //TODO: si ricarica, ovvero, fa la sleep, si sposta, batteria al 100%


                synchronized (rechargeStateLock) {
                    rechargeState = 0;
                    timestampOfRequest = 0;
                }
                PendingRechargeRequestQueue.getInstance().senOkToAllPendingRequests();

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



    public int getRechargeState() {
        synchronized (rechargeStateLock) {
            return rechargeState;
        }
    }

    public double getTimestampOfRequest() {
        synchronized (rechargeStateLock) {
            return timestampOfRequest;
        }
    }
}
