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


    private double timestampOfRequest;

    public BatteryManager(Taxi t, BatteryListener bl){
        thisTaxi = t;
        acks = 0;
        updateAcks = new Object();
        canRecharge = new Object();
    }

    public void run(){

        while(thisTaxi.getState() != Commons.EXITING){
            synchronized(thisTaxi.alertBatteryRecharge){
                try {
                    thisTaxi.alertBatteryRecharge.wait();
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.setState(Commons.WANT_TO_RECHARGE);
                        timestampOfRequest = System.currentTimeMillis();
                    }
                } catch (InterruptedException e) {throw new RuntimeException(e);}
                //will wake up when the battery is below 30% OR when an explicit request of recharge is given
                //...or when it was explicitly requested for the taxi to finish its execution (in that case,
                //the next "if" will be evaluated to false)
            }

            District currentDistrict = SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY());

            if(thisTaxi.getState() != Commons.EXITING){
                acks = 0;
                synchronized (thisTaxi.otherTaxisLock){
                    currentParticipants = thisTaxi.getOtherTaxis().size();
                    Thread[] a = new Thread[currentParticipants];
                    int i = 0;
                    for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : thisTaxi.getOtherTaxis().entrySet()){
                        //for each taxi that in the city, ask him if I can recharge

                        //and I also launch a new thread that will ask that taxi the permission for accessing
                        //the recharge station
                        Thread t = new Thread(new BatteryRequest(thisTaxi, this, entry.getValue(),
                                timestampOfRequest, currentDistrict));
                        a[i] = t;
                        i++;
                    }
                    for(Thread t : a){
                        t.start();
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

                //canRecharge.notify() could be invoked even if I'm asked to terminate. In that case, I don't
                //recharge, but instead, I simply reply ok to all the taxis that were waiting
                if(thisTaxi.getState() != Commons.EXITING){

                    /*Moreover, when a taxi acquires rights
                    to recharge its battery:*/

                    /*it consumes 1% of its battery level for each kilometer traveled to reach
                    the recharge station*/
                    int[] rechargeCoordinates = SmartCity.getCoordinatesForRechargeStation(
                            SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY()));

                    int distance = (int) SmartCity.distance(thisTaxi.getCurrX(), thisTaxi.getCurrY(),
                            rechargeCoordinates[0], rechargeCoordinates[1]);

                    thisTaxi.subtractPercentageFromBatteryLevel(distance);

                    /*its position becomes the same as the cell of the recharge station of the
                    district in which the taxi is currently positioned.*/
                    synchronized (thisTaxi.incomingRequests_lock) {
                        thisTaxi.setCurrX(rechargeCoordinates[0]);
                        thisTaxi.setCurrY(rechargeCoordinates[1]);
                    }

                    /*The recharging operation is simulated through a Thread.sleep() of 10
                    seconds.*/
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.setState(Commons.IDLE);
                        timestampOfRequest = 0;
                        thisTaxi.setBatteryLevel(100);
                        //we also notify the idle thread that the recharge process has completed

                    }
                }

                thisTaxi.getQueue().sendOkToAllPendingRequests();


            }

            synchronized (thisTaxi.rechargeComplete_lock){
                thisTaxi.rechargeComplete_lock.notify();
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

}
