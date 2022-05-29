package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass;
import ride.request.RideRequestMessageOuterClass.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class IdleThread implements Runnable{

    private Taxi thisTaxi;
    private MqttClient client;
    private String broker;
    private String clientId;
    private int qos;


    private Object incomingRequests_lock;
    private ArrayList<RideRequestMessage> incomingRequests;






    private boolean DEBUG_LOCAL = true;

    public IdleThread(Taxi t){
        thisTaxi = t;
        incomingRequests_lock = new Object();
        incomingRequests = new ArrayList<>();
        //let's start the MQTT client
        broker = "tcp://localhost:1883";
        clientId = MqttClient.generateClientId();
        qos = 2;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            System.out.println("Taxi " + thisTaxi.getId() + " connecting to Broker " + broker);
            client.connect(connOpts);
            System.out.println("Taxi " + thisTaxi.getId() + " connected");


            // Callback
            client.setCallback(new MqttCallback() {

                public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {
                    // Called when a message arrives from the server that matches any subscription made by the client

                    //when a message arrives, it's because we received a ride request for the district in which we were
                    //at the moment the request was issued. We simply append this message to the queue and notify the
                    //thread that a new request has arrived
                    RideRequestMessage rrm = RideRequestMessage.parseFrom(message.getPayload());
                    addIncomingRequest(rrm);
                    //String receivedMessage = rrm.getId() + " - (" + rrm.getStartingX() + "," + rrm.getStartingY() + ") - (" + +rrm.getArrivingX() + "," + rrm.getArrivingY() + ")";
                    debug("Taxi " + thisTaxi.getId() + " received a request for position (" + rrm.getStartingX() + "," + rrm.getStartingY() + ")");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connectionlost! cause:" + cause.getMessage() + "-  Thread PID: " + Thread.currentThread().getId());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used here
                }

            });

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }

        //first thing first: the taxi subscribes to the district to which it belongs right now
        District d = SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY());
        subscribeToADistrictTopic(d);

        //now, the Taxi can notify Seta that he is now present in this initial district.
        RideRequestMessageOuterClass.District true_d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
        switch(d){
            case DISTRICT1: true_d = RideRequestMessageOuterClass.District.DISTRICT1; break;
            case DISTRICT2: true_d = RideRequestMessageOuterClass.District.DISTRICT2; break;
            case DISTRICT3: true_d = RideRequestMessageOuterClass.District.DISTRICT3; break;
            case DISTRICT4: true_d = RideRequestMessageOuterClass.District.DISTRICT4; break;
            case DISTRICT_ERROR: true_d = RideRequestMessageOuterClass.District.DISTRICT_ERROR; break;
        }
        try {
            client.publish(Commons.topicMessageArrivedInDistrict, new MqttMessage(NotifyFromTaxi.newBuilder().setDistrict(true_d).build().toByteArray()));
        } catch (MqttException e) {throw new RuntimeException(e);}

        debug("finished the constructor");
    }

    @Override
    public void run() {


        debug("hi there");

        while(true) { //TODO: !taxiHasToTerminate
            //first of all, let's check if we have to recharge the battery
            if(thisTaxi.getBatteryLevel() <= 30){
                //if so, now we have to recharge
                synchronized (thisTaxi.alertBatteryRecharge) {
                    thisTaxi.alertBatteryRecharge.notify();
                }

                //we then wait until the recharge is complete
                synchronized (thisTaxi.rechargeComplete_lock){
                    try {
                        thisTaxi.rechargeComplete_lock.wait();
                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }

                //TODO: potrei essere svegliato anche quando in realtà devo uscire. In tal caso,
                //if(devoUscire), basta
            }



            //if there are pending ride requests, take the first available one and ask the others about it.
            //clearly, the one chosen must be of my current district
            RideRequestMessage currentRideRequest;
            while((currentRideRequest = getIncomingRequest()) != null){ //while there is at least one pending request...
                //let's start the election process. I have to contact all the taxis currently present in the city
                //and ask them if I can take care of this ride.
                askOtherTaxisAboutARide(currentRideRequest);
            }

            synchronized (incomingRequests_lock){
                try {
                    //we wait until there is a new request in the queue.
                    incomingRequests_lock.wait();
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }



        }
        //This thread has the role of hearing the ride requests it receives and to fulfill them.
        //fulfilling means also coordinating with other taxis and eventually letting someone else take care of them.


        /*
        //just for trying

        while(thisTaxi.getBatteryLevel() >= 30){

            try {
                Thread.sleep(250);
                thisTaxi.subtractPercentageFromBatteryLevel(10);
                debug("battery level: " + thisTaxi.getBatteryLevel());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        synchronized (thisTaxi.alertBatteryRecharge) {
            thisTaxi.alertBatteryRecharge.notify();
        }
        */


    }


    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL) System.out.println("debug: " + msg);
    }



    private void subscribeToADistrictTopic(District d){



        String topic = "seta/smartcity/rides/" + d.toString().toLowerCase();
        try{

            //if the client was already subscribed to a district, it must now unsubscribe from them
            client.unsubscribe("seta/smartcity/rides/+");   //TODO: lancia errore se non è registrato? devo mettere un array?

            System.out.println(thisTaxi.getId() + " subscribing to district " + d.toString().toLowerCase());
            client.subscribe(topic, qos);
            System.out.println(thisTaxi.getId() + " subscribed to district " + d.toString().toLowerCase());

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
        debug("ok, subscribed!");
    }

    private void closeMqttSubscriberConnection(){
        try {
            client.disconnect();
        }catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
    }



    public void addIncomingRequest(RideRequestMessage rrm){
        synchronized (incomingRequests_lock){
            incomingRequests.add(rrm);
            incomingRequests_lock.notify();
        }
    }

    public RideRequestMessage getIncomingRequest(){
        RideRequestMessage ret = null;
        synchronized (incomingRequests_lock){
            if(incomingRequests.size() > 0) {
                ret = incomingRequests.remove(0);
            }
        }
        return ret;
    }

    private void askOtherTaxisAboutARide(RideRequestMessage currentRideRequest){

    }


}
