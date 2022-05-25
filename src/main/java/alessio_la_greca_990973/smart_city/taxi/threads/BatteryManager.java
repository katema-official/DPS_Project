package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
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
    private PendingRechargeRequestQueue queue;

    private BatteryListener batteryListener;
    private boolean DEBUG_LOCAL = true;

    private double timestampOfRequest;

    public BatteryManager(Taxi t){
        thisTaxi = t;
        acks = 0;
        updateAcks = new Object();
        canRecharge = new Object();
        queue = new PendingRechargeRequestQueue();

        //for correctly handling the recharge request of the current district, this taxi needs also to be always
        //ready to reply to another taxi that asks him the permission to go recharge. To do this, we create another
        //thread that simply listens to other taxis' recharge requests.
        batteryListener = new BatteryListener(thisTaxi, this, queue);
        Thread th = new Thread(batteryListener);
        th.start();
    }

    public void run(){
        while(true){		//!taxiMustTerminate
            synchronized(thisTaxi.alertBatteryRecharge){
                try {
                    debug("waiting for recharge request...");
                    thisTaxi.alertBatteryRecharge.wait();
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.setState(Commons.WANT_TO_RECHARGE);
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
                        //if there are no participants, the taxi can recharge without problems
                        if(currentParticipants > 0) {
                            canRecharge.wait();
                        }

                        thisTaxi.setState(Commons.RECHARGING);  //already synchronized in setState()

                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }

                //TODO: si ricarica, ovvero, fa la sleep, si sposta, batteria al 100%
                /*Moreover, when a taxi acquires rights
                to recharge its battery:*/

                /*it consumes 1% of its battery level for each kilometer traveled to reach
                the recharge station*/
                int[] rechargeCoordinates = SmartCity.getCoordinatesForRechargeStation(
                        SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY()));

                int distance = SmartCity.distance(thisTaxi.getCurrX(), thisTaxi.getCurrY(),
                        rechargeCoordinates[0], rechargeCoordinates[1]);

                /*its position becomes the same as the cell of the recharge station of the
                district in which the taxi is currently positioned.*/
                thisTaxi.setCurrX(rechargeCoordinates[0]);
                thisTaxi.setCurrX(rechargeCoordinates[1]);

                debug("Starting to recharge...");

                /*The recharging operation is simulated through a Thread.sleep() of 10
                seconds.*/
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                debug("Recharge finished!");

                synchronized (thisTaxi.stateLock) {
                    thisTaxi.setState(Commons.IDLE);
                    timestampOfRequest = 0;
                }
                queue.sendOkToAllPendingRequests();


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

    public double getTimestampOfRequest() {
        return timestampOfRequest;
    }

    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + msg);
        }
    }
}
