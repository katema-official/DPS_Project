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
import taxis.service.MiscTaxiServiceOuterClass;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.util.*;

public class IdleThread implements Runnable{

    private Taxi thisTaxi;
    private MqttClient client;
    private String broker;
    private String clientId;
    private int qos;


    //hashmap that contains all the requests arrived. For each of them, I save if it has already been satisfied
    //(true) or not (false)
    private HashMap<RideRequestMessage, Boolean> incomingRequests;



    //used to track the current ID of the request for which i'm running the election algorithm
    public int currentRequestBeingProcessed;
    public RideRequestMessage rrmCurrentRequestBeingProcessed;
    //used to track the other Taxis participating to this election
    public HashMap<Integer, TaxiTaxiRepresentation> otherTaxisInThisElection;
    public boolean otherTaxisInThisElection_ready;
    public Object election_lock;


    private StatisticsThread statisticsThread;


    public IdleThread(Taxi t, StatisticsThread statisticsThread){
        thisTaxi = t;
        otherTaxisInThisElection = new HashMap<>(thisTaxi.getOtherTaxis());
        otherTaxisInThisElection_ready = false;

        incomingRequests = new HashMap<>();
        //let's start the MQTT client
        broker = "tcp://localhost:1883";
        clientId = MqttClient.generateClientId();
        qos = 2;

        currentRequestBeingProcessed = -1;
        rrmCurrentRequestBeingProcessed = null;
        election_lock = new Object();

        this.statisticsThread = statisticsThread;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            client.connect(connOpts);


            // Callback
            client.setCallback(new MqttCallback() {

                public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {
                    // Called when a message arrives from the server that matches any subscription made by the client

                    //when a message arrives, it's because we received a ride request for the district in which we were
                    //at the moment the request was issued. We simply append this message to the queue and notify the
                    //thread that a new request has arrived
                    RideRequestMessage rrm = RideRequestMessage.parseFrom(message.getPayload());
                    addIncomingRequest(rrm);
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
        subscribeToADistrictTopic(d, District.DISTRICT_ERROR);
    }

    @Override
    public void run() {

        while(thisTaxi.getState() != Commons.EXITING) {
            //first of all, let's check if we have to recharge the battery
            if(thisTaxi.getBatteryLevel() <= 30 || thisTaxi.getExplicitRechargeRequest() == true){
                //if so, now we have to recharge
                synchronized (thisTaxi.alertBatteryRecharge) {
                    thisTaxi.alertBatteryRecharge.notify();
                }

                //we then wait until the recharge is complete
                synchronized (thisTaxi.rechargeComplete_lock){
                    try {
                        thisTaxi.rechargeComplete_lock.wait();
                        thisTaxi.setExplicitRechargeRequest(false);
                    } catch (InterruptedException e) {throw new RuntimeException(e);}
                }

                //If I've been awakend not because every other taxi replyed ok to me but because
                //I need to leave, I
                //sa
                if(thisTaxi.getState() == Commons.EXITING){
                    break;
                }
            }

            //if there are pending ride requests, take the first available one and ask the others about it.
            //clearly, the one chosen must be of my current district
            RideRequestMessage currentRideRequest = getIncomingRequest();
            if(currentRideRequest != null){ //if there is at least one pending request...
                //I save that this is the request I'm taking care of right now.
                synchronized (thisTaxi.stateLock) {
                    currentRequestBeingProcessed = currentRideRequest.getId();
                    rrmCurrentRequestBeingProcessed = currentRideRequest;
                    thisTaxi.setState(Commons.ELECTING);
                }
                //let's start the election process. I have to contact all the taxis currently present in the city
                //and ask them if I can take care of this ride.

                boolean ret = false;
                ret = askOtherTaxisAboutARide(currentRideRequest);
                //when ret=true, it means I've been chosen to take care of this ride request


                if(ret){
                    System.out.println("I HANDLE REQUEST NUMBER " + currentRequestBeingProcessed);
                    double toLower1 = 0D;
                    double toLower2 = 0D;
                    setIncomingRequestToTrue(currentRequestBeingProcessed);
                    //we tell everyone that this request has been satisfied
                    tellOtherTaxisThatIHandleThisRequest(currentRequestBeingProcessed);
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.setState(Commons.RIDING);

                        int oldX = thisTaxi.getCurrX();
                        int oldY = thisTaxi.getCurrY();

                        //now, before sleeping for 5 seconds, I:
                        //1) I unsubscribe from my district and subscribe to the one toward which I'm heading
                        District oldDistrict = SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY());
                        District newDistrict = SmartCity.getDistrict(
                                currentRideRequest.getArrivingX(), currentRideRequest.getArrivingY());
                        subscribeToADistrictTopic(newDistrict, oldDistrict);

                        //2)send an ack to SETA regarding this ride
                        RideRequestMessageOuterClass.District d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
                        switch (SmartCity.getDistrict(
                                rrmCurrentRequestBeingProcessed.getStartingX(),
                                rrmCurrentRequestBeingProcessed.getStartingY())) {
                            case DISTRICT1: d = RideRequestMessageOuterClass.District.DISTRICT1; break;
                            case DISTRICT2: d = RideRequestMessageOuterClass.District.DISTRICT2; break;
                            case DISTRICT3: d = RideRequestMessageOuterClass.District.DISTRICT3; break;
                            case DISTRICT4: d = RideRequestMessageOuterClass.District.DISTRICT4; break;
                            default: d = RideRequestMessageOuterClass.District.DISTRICT_ERROR; break;
                        }
                        AckFromTaxi ack = AckFromTaxi.newBuilder().setIdRequest(currentRideRequest.getId()).setDistrict(d).build();
                        MqttMessage message = new MqttMessage(ack.toByteArray());
                        message.setQos(qos);
                        try {
                            client.publish(Commons.topicMessagesAcks, message);

                        } catch (MqttException e) {
                            e.printStackTrace();
                        }

                        //3) lower the battery value
                        toLower1 = (int) SmartCity.distance(oldX, oldY,
                                rrmCurrentRequestBeingProcessed.getStartingX(), rrmCurrentRequestBeingProcessed.getStartingY());
                        thisTaxi.subtractPercentageFromBatteryLevel((int) toLower1);
                        toLower2 = (int) SmartCity.distance(rrmCurrentRequestBeingProcessed.getStartingX(),
                                rrmCurrentRequestBeingProcessed.getStartingY(),
                                rrmCurrentRequestBeingProcessed.getArrivingX(),
                                rrmCurrentRequestBeingProcessed.getArrivingY());
                        thisTaxi.subtractPercentageFromBatteryLevel((int) toLower2);
                    }



                    //5) add the statistics to the proper data structure
                    statisticsThread.addKilometers(toLower1 + toLower2);
                    statisticsThread.addRide();

                    //6) now I actually sleep
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {e.printStackTrace();}
                }

            }else{
                synchronized (thisTaxi.stateLock) {
                    thisTaxi.setState(Commons.IDLE);
                }
            }

            if(getSizeOfIncomingRequestQueue() == 0) {
                synchronized (thisTaxi.incomingRequests_lock) {
                    try {
                        //we wait until there is a new request in the queue.
                        System.out.println("waiting");
                        thisTaxi.incomingRequests_lock.wait();
                        System.out.println("awakened");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }


        }
        //the thread comes here when we tell the taxi to stop
        closeMqttSubscriberConnection();


    }

    private void tellOtherTaxisThatIHandleThisRequest(int currentRequestBeingProcessed) {
        HashMap<Integer, TaxiTaxiRepresentation> others;
        synchronized (thisTaxi.otherTaxisLock) {
            others = new HashMap<>(thisTaxi.getOtherTaxis());
        }

        MiscTaxiServiceOuterClass.District true_d = MiscTaxiServiceOuterClass.District.DISTRICT_ERROR;
        switch(SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY())){
            case DISTRICT1: true_d = MiscTaxiServiceOuterClass.District.DISTRICT1; break;
            case DISTRICT2: true_d = MiscTaxiServiceOuterClass.District.DISTRICT2; break;
            case DISTRICT3: true_d = MiscTaxiServiceOuterClass.District.DISTRICT3; break;
            case DISTRICT4: true_d = MiscTaxiServiceOuterClass.District.DISTRICT4; break;
            case DISTRICT_ERROR: true_d = MiscTaxiServiceOuterClass.District.DISTRICT_ERROR; break;

        }
        synchronized(election_lock){
            for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : others.entrySet()) {


                //for each taxi that is in the city, tell him that this request has been handled
                String host = entry.getValue().getHostname();
                int port = entry.getValue().getListeningPort();
                ManagedChannel channel = ManagedChannelBuilder.forTarget(host + ":" + port).usePlaintext().build();
                MiscTaxiServiceBlockingStub stub = MiscTaxiServiceGrpc.newBlockingStub(channel);

                //let's build the request
                SatisfiedRequest done = SatisfiedRequest.newBuilder().setReqId(currentRequestBeingProcessed)
                                .setDistrict(true_d).build();

                MiscTaxiServiceOuterClass.Void reply = stub.iTookCareOfThisRequest(done);
                channel.shutdown();


            }
        }

    }



    private void subscribeToADistrictTopic(District newD, District oldD){
        //it makes sense to unsubscribe and re-subscribe to another district only if the two are different

        if(newD != oldD){
            String topic = "seta/smartcity/rides/" + newD.toString().toLowerCase();
            System.out.println("Changing district: from " + oldD + " to " + newD);
            try{



                //-------------------------------------------------------------------------------------------
                //NOW, LISTEN CLOSELY
                //if I'm actually changing district, I can remove ALL of my pending requests, because
                //they now belong to a district different from mine.

                synchronized (thisTaxi.incomingRequests_lock){
                    //if the client was already subscribed to a district, it must now unsubscribe from them
                    client.unsubscribe("seta/smartcity/rides/+");
                    incomingRequests.clear();
                    client.subscribe(topic, qos);

                    //update my coordinates to the one of the final location of the ride (if this method was invoked
                    //because of a ride)
                    if(rrmCurrentRequestBeingProcessed != null) {
                        thisTaxi.setCurrX(rrmCurrentRequestBeingProcessed.getArrivingX());
                        thisTaxi.setCurrY(rrmCurrentRequestBeingProcessed.getArrivingY());
                    }
                }

                //--------------------------------------------------------------------------------------------



            } catch (MqttException me) {
                System.out.println("reason " + me.getReasonCode());
                System.out.println("msg " + me.getMessage());
                System.out.println("loc " + me.getLocalizedMessage());
                System.out.println("cause " + me.getCause());
                System.out.println("excep " + me);
                me.printStackTrace();
            }

            //now, the Taxi can notify Seta that he is now present in this district.
            RideRequestMessageOuterClass.District true_d = RideRequestMessageOuterClass.District.DISTRICT_ERROR;
            switch(newD){
                case DISTRICT1: true_d = RideRequestMessageOuterClass.District.DISTRICT1; break;
                case DISTRICT2: true_d = RideRequestMessageOuterClass.District.DISTRICT2; break;
                case DISTRICT3: true_d = RideRequestMessageOuterClass.District.DISTRICT3; break;
                case DISTRICT4: true_d = RideRequestMessageOuterClass.District.DISTRICT4; break;
                case DISTRICT_ERROR: true_d = RideRequestMessageOuterClass.District.DISTRICT_ERROR; break;
            }
            try {
                client.publish(Commons.topicMessageArrivedInDistrict, new MqttMessage(NotifyFromTaxi.newBuilder().setDistrict(true_d).build().toByteArray()));
            } catch (MqttException e) {throw new RuntimeException(e);}
        }else{
            //in this case we simply update the position of the taxi
            if(rrmCurrentRequestBeingProcessed != null) {
                thisTaxi.setCurrX(rrmCurrentRequestBeingProcessed.getArrivingX());
                thisTaxi.setCurrY(rrmCurrentRequestBeingProcessed.getArrivingY());
            }
        }
    }

    public void closeMqttSubscriberConnection(){
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

        int unsatisfied = 0;
        synchronized (thisTaxi.incomingRequests_lock){

            for(Map.Entry<RideRequestMessage, Boolean> entry : incomingRequests.entrySet()){
                //the minimum i want has to be:
                //1) the minimum RideRequestMessage among all of the onse i have in my incomingRequests
                //2) this minimum must have false as value, beacuse it means it hasn't been satisfied yet (for what I know at least)
                if(entry.getValue() == false){
                    unsatisfied++;
                }
            }
        }
        System.out.println("unsatisfied = " + unsatisfied);
        return unsatisfied;
    }

    public void addIncomingRequest(RideRequestMessage rrm){
        synchronized (thisTaxi.incomingRequests_lock){
            if(SmartCity.getDistrict(rrm.getStartingX(), rrm.getStartingY()) != SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY())) return;
            for(Map.Entry<RideRequestMessage, Boolean> entry : incomingRequests.entrySet()){
                RideRequestMessage req = entry.getKey();
                if(req.getId() == rrm.getId()){
                    return;
                }
            }
            incomingRequests.put(rrm, false);   //for me, initially, the new request has not been satisfied
            System.out.println("Arrived request " + rrm.getId() + " for the district " + SmartCity.getDistrict(rrm.getStartingX(), rrm.getStartingY()) +
                    " to the district " + SmartCity.getDistrict(rrm.getArrivingX(), rrm.getArrivingY()));
            thisTaxi.incomingRequests_lock.notify();
        }
    }

    public RideRequestMessage getIncomingRequest(){
        RideRequestMessage ret = null;
        synchronized (thisTaxi.incomingRequests_lock){
            //I have to get the first available request of my district (subscribeToADistrictTopic() guarantees me
            //that in the queue there will be only requests of the district in which I'm currently in)
            int minReq = Integer.MAX_VALUE;
            for(Map.Entry<RideRequestMessage, Boolean> entry : incomingRequests.entrySet()){
                //the minimum i want has to be:
                //1) the minimum RideRequestMessage among all of the onse i have in my incomingRequests
                //2) this minimum must have false as value, beacuse it means it hasn't been satisfied yet (for what I know at least)
                if(entry.getValue() == false && entry.getKey().getId() < minReq){
                    minReq = entry.getKey().getId();
                    ret = entry.getKey();
                }
            }
        }
        System.out.println("returned incoming request = " + (ret != null ? ret.getId() : null));

        return ret;
    }


    public boolean getIncomingRequestValue(int id){
        synchronized (thisTaxi.incomingRequests_lock){
            //I have to get the first available request of my district (subscribeToADistrictTopic() guarantees me
            //that in the queue there will be only requests of the district in which I'm currently in)
            for(Map.Entry<RideRequestMessage, Boolean> entry : incomingRequests.entrySet()){
                //the minimum i want has to be:
                //1) the minimum RideRequestMessage among all of the onse i have in my incomingRequests
                //2) this minimum must have false as value, beacuse it means it hasn't been satisfied yet (for what I know at least)
                if(entry.getKey().getId() == id){
                    if(entry.getValue() == true){
                        return true;
                    }else{
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public void setIncomingRequestToTrue(int reqId){
        synchronized (thisTaxi.incomingRequests_lock) {
            for (Map.Entry<RideRequestMessage, Boolean> entry : incomingRequests.entrySet()) {
                if (entry.getKey().getId() == reqId) {
                    entry.setValue(true);
                }
            }
        }
    }




    private boolean askOtherTaxisAboutARide(RideRequestMessage currentRideRequest){
        synchronized (thisTaxi.otherTaxisLock) {
            otherTaxisInThisElection = new HashMap<>(thisTaxi.getOtherTaxis());
        }
        //I have to ask only to the taxis I didn't ask before
        synchronized(election_lock){
            for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : otherTaxisInThisElection.entrySet()) {


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
                if (reply.getOk() == false) {
                    setIncomingRequestToTrue(currentRideRequest.getId());
                    return false;
                }

            }
        }
        //if instead all of the other taxis replied to me with an actual ok message (happens also if there were not
        //other taxis to contact), yeah, I can take care of this ride!
        return true;
    }





    public boolean isThisTaxiInThisElection(int taxiId){
        if(otherTaxisInThisElection.containsKey(taxiId)){
            return true;
        }
        return false;
    }


    //true: reply yes to him
    //false: reply no to him

    //false = I win (I will maybe take care of this request)
    //true = the other wins (you will not, for sure, take care of this request)
    public boolean compareTaxis(TaxiCoordinationRequest other){
        Taxi me = thisTaxi;

        //*the Taxi must have the minimum distance from the starting point of
        //the ride*
        double myDistance = SmartCity.distance(me.getCurrX(), me.getCurrY(),
                rrmCurrentRequestBeingProcessed.getStartingX(), rrmCurrentRequestBeingProcessed.getStartingY());
        double otherDistance = SmartCity.distance(other.getX(), other.getY(),
                rrmCurrentRequestBeingProcessed.getStartingX(), rrmCurrentRequestBeingProcessed.getStartingY());
        if(myDistance - otherDistance < 0){ //i'm closer
            return false;
        }else if(myDistance - otherDistance > 0.01){    //he's closer
            return true;
        }else{
            //*if more taxis meet the previous criteria, it must be chosen among them
            //the Taxi with the highest battery level*
            if(me.getBatteryLevel() > other.getBatteryLevel()){
                return false;
            }else if (me.getBatteryLevel() < other.getBatteryLevel()){
                return true;
            }else{
                //*if more taxis meet the previous criteria, it must be chosen among them
                //the Taxi with the highest ID*
                if(me.getId() > other.getTaxiId()){
                    return false;
                }else{
                    return true;
                }
            }
        }

    }

}
