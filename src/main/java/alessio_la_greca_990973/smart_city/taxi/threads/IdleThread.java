package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass;
import ride.request.RideRequestMessageOuterClass.*;
import taxis.service.MiscTaxiServiceGrpc.*;
import taxis.service.MiscTaxiServiceGrpc;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.util.*;

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
            if((currentRideRequest = getIncomingRequest()) != null){ //if there is at least one pending request...
                //I save that this is the request I'm taking care of right now.
                synchronized (thisTaxi.stateLock) {
                    thisTaxi.currentRequestBeingProcessed = currentRideRequest.getId();
                    thisTaxi.setState(Commons.ELECTING);
                }
                //let's start the election process. I have to contact all the taxis currently present in the city
                //and ask them if I can take care of this ride.
                boolean ret = askOtherTaxisAboutARide(currentRideRequest);
                //(in any case, the election is finished)
                thisTaxi.currentRequestBeingProcessed = -1;
                //when ret=true, it means I've been chosen to take care of this ride request
                if(ret){
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.currentRequestBeingProcessed = -1;
                        thisTaxi.setState(Commons.RIDING);
                        thisTaxi.satisfiedRides.put(
                                SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY()),
                                currentRideRequest.getId());

                        //now, before sleeping for 5 seconds, I:
                        //1)send an ack to SETA regarding this ride
                        RideRequestMessageOuterClass.District d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
                        switch (SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY())) {
                            case DISTRICT1:
                                d = RideRequestMessageOuterClass.District.DISTRICT1;
                                break;
                            case DISTRICT2:
                                d = RideRequestMessageOuterClass.District.DISTRICT2;
                                break;
                            case DISTRICT3:
                                d = RideRequestMessageOuterClass.District.DISTRICT3;
                                break;
                            case DISTRICT4:
                                d = RideRequestMessageOuterClass.District.DISTRICT4;
                                break;
                            default:
                                d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
                                break;
                        }
                        AckFromTaxi ack = AckFromTaxi.newBuilder().setIdRequest(currentRideRequest.getId())
                                .setDistrict(d).build();
                        MqttMessage message = new MqttMessage(ack.toByteArray());
                        message.setQos(qos);
                        try {
                            client.publish(Commons.topicMessagesAcks, message);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }

                        //2) change my district from the current one to the one toward which I'm heading
                        District newDistrict = SmartCity.getDistrict(
                                currentRideRequest.getArrivingX(), currentRideRequest.getArrivingY());
                        subscribeToADistrictTopic(newDistrict);

                        //3) ask to SETA to send me the pendig requests of the district in which
                        //I'm about to go (I prepare myself for after the ride
                        d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
                        switch (newDistrict) {
                            case DISTRICT1:
                                d = RideRequestMessageOuterClass.District.DISTRICT1;
                                break;
                            case DISTRICT2:
                                d = RideRequestMessageOuterClass.District.DISTRICT2;
                                break;
                            case DISTRICT3:
                                d = RideRequestMessageOuterClass.District.DISTRICT3;
                                break;
                            case DISTRICT4:
                                d = RideRequestMessageOuterClass.District.DISTRICT4;
                                break;
                            default:
                                d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
                                break;
                        }
                        NotifyFromTaxi notify = NotifyFromTaxi.newBuilder().setDistrict(d).build();
                    }
                    //4) TODO: add the statistics to the proper data structure

                    //5) now I actually sleep
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {e.printStackTrace();}


                }else{
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.currentRequestBeingProcessed = -1;
                        thisTaxi.setState(Commons.ELECTING);
                    }
                }


            }

            if(getSizeOfIncomingRequestQueue() == 0) {
                synchronized (incomingRequests_lock) {
                    try {
                        //we wait until there is a new request in the queue.
                        incomingRequests_lock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
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


    public int getSizeOfIncomingRequestQueue(){
        synchronized (incomingRequests_lock){
            return incomingRequests.size();
        }
    }

    public void addIncomingRequest(RideRequestMessage rrm){
        synchronized (incomingRequests_lock){
            //add the request only if it is not already present OR we know it's already been satisfied
            for(RideRequestMessage req : incomingRequests){
                if(req.getId() == rrm.getId() ||
                    thisTaxi.satisfiedRides.get(SmartCity.getDistrict(rrm.getStartingX(), rrm.getStartingY())) >
                        rrm.getId()){
                    return;
                }
            }
            incomingRequests.add(rrm);
            incomingRequests_lock.notify();
        }
    }

    public RideRequestMessage getIncomingRequest(){
        RideRequestMessage ret = null;
        synchronized (incomingRequests_lock){
            //I have to get the first available request of my district. In the queue, in fact,
            //there might still be requests belonging to a district i abandoned because of a ride
            //of which i took care of.
            boolean foundOne = false;
            while(foundOne == false && incomingRequests.size() > 0){

                ret = incomingRequests.remove(0);
                //if the removed request is from the district in which I'm currently in,
                //return it
                if(SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY()) ==
                    SmartCity.getDistrict(ret.getStartingX(), ret.getStartingY())){
                        foundOne = true;
                }

            }
        }
        return ret;
    }

    private boolean askOtherTaxisAboutARide(RideRequestMessage currentRideRequest){
        synchronized (thisTaxi.otherTaxisLock) {
            thisTaxi.otherTaxisInThisElection = new HashMap<>(thisTaxi.getOtherTaxis());
        }
        synchronized(thisTaxi.election_lock){
            for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : thisTaxi.otherTaxisInThisElection.entrySet()){
                //for each taxi that in the city, ask him if you can take care of request
                String host = entry.getValue().getHostname();
                int port = entry.getValue().getListeningPort();
                ManagedChannel channel = ManagedChannelBuilder.forTarget(host + ":" + port).usePlaintext().build();
                MiscTaxiServiceBlockingStub stub = MiscTaxiServiceGrpc.newBlockingStub(channel);

                //let's build the request
                TaxiCoordinationRequest request =
                        TaxiCoordinationRequest.newBuilder().setIdRideRequest(currentRideRequest.getId())
                                .setX(thisTaxi.getCurrX()).setY(thisTaxi.getCurrY())
                                .setBatteryLevel(thisTaxi.getBatteryLevel())
                                .setTaxiId(thisTaxi.getId()).build();

                TaxiCoordinationReply reply = stub.mayITakeCareOfThisRequest(request);
                channel.shutdown();

                //if EVEN ONE of the other taxis tells me to not take care of this request,
                //I stop asking. I know i'll have to take care of it if all of them reply
                //to me with an actual ok message
                if(reply.getOk() == false){
                    return false;
                }
            }
        }
        //if instead all of the other taxis replied to me with an actual ok message,
        //yeah, I can take care of this ride!
        return true;
    }


}
