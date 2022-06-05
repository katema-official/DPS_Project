package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
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



    //used to track the current ID of the request for which i'm running the election algorithm
    public int currentRequestBeingProcessed;
    public RideRequestMessage rrmCurrentRequestBeingProcessed;
    //uset to track the other Taxis participating to this election
    public HashMap<Integer, TaxiTaxiRepresentation> otherTaxisInThisElection;
    public Object election_lock;


    //list that contains the pending "election" requests, that is, requests
    //for understanding who has to take care of a specific request that this Taxi
    //still hasn't processed.
    private HashMap<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> pendingRideElectionRequests;
    private Object pendingRideElectionRequests_lock;



    private StatisticsThread statisticsThread;

    private boolean DEBUG_LOCAL = true;
    private boolean DEBUG_LOCAL2 = true;

    public IdleThread(Taxi t, StatisticsThread statisticsThread){
        thisTaxi = t;
        otherTaxisInThisElection = new HashMap<>(thisTaxi.getOtherTaxis());

        incomingRequests_lock = new Object();
        incomingRequests = new ArrayList<>();
        //let's start the MQTT client
        broker = "tcp://localhost:1883";
        clientId = MqttClient.generateClientId();
        qos = 2;
        pendingRideElectionRequests = new HashMap<>();

        currentRequestBeingProcessed = -1;
        rrmCurrentRequestBeingProcessed = null;
        election_lock = new Object();
        pendingRideElectionRequests_lock = new Object();

        this.statisticsThread = statisticsThread;

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
                    //debug("Taxi " + thisTaxi.getId() + " received request number " + rrm.getId());//a request for position (" + rrm.getStartingX() + "," + rrm.getStartingY() + ")");

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



        debug("finished the constructor");
    }

    @Override
    public void run() {


        while(true) { //TODO: !taxiHasToTerminate
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

                //TODO: potrei essere svegliato anche quando in realtà devo uscire. In tal caso,
                //if(devoUscire), basta
            }

            //debug("(Taxi " + thisTaxi.getId() + "): my position is (" + thisTaxi.getCurrX() + ","+ thisTaxi.getCurrY() + ")");

            //if there are pending ride requests, take the first available one and ask the others about it.
            //clearly, the one chosen must be of my current district
            RideRequestMessage currentRideRequest;
            if((currentRideRequest = getIncomingRequest()) != null){ //if there is at least one pending request...
                //I save that this is the request I'm taking care of right now.
                synchronized (thisTaxi.stateLock) {
                    //debug("(Taxi " + thisTaxi.getId() + "): starting to take care of request number " + currentRideRequest.getId() +
                    //        " (" + currentRideRequest.getStartingX()  +"," + currentRideRequest.getStartingY() + ") -> ("
                    //        + currentRideRequest.getArrivingX()  +"," + currentRideRequest.getArrivingY() + ")");
                    currentRequestBeingProcessed = currentRideRequest.getId();
                    rrmCurrentRequestBeingProcessed = currentRideRequest;
                    thisTaxi.setState(Commons.ELECTING);
                }
                //let's start the election process. I have to contact all the taxis currently present in the city
                //and ask them if I can take care of this ride.

                //BUT, before doing that, we have to answer to eventual pending requests for rides arrived in the
                //past. If we reply "yes" to even one of them, it means that we won't anyway take care of this
                //request.
                HashMap<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> pendingElections =
                        getPendingRideElectionRequestForSpecificRequest(currentRequestBeingProcessed);
                boolean stop = false;
                for (Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e : pendingElections.entrySet()){
                    //debug("(Taxi " + thisTaxi.getId() + "): now I handle the deffered request with id " +
                    //        rrmCurrentRequestBeingProcessed.getId() + " for the other taxi " + e.getKey().getTaxiId());
                    boolean tmp = compareTaxis(e.getKey());

                    if(tmp){
                        //the other one wins
                        e.getValue().onNext(TaxiCoordinationReply.newBuilder().setOk(true).build());
                        e.getValue().onCompleted();
                        //debug("(taxi " + thisTaxi.getId() + " received from taxi " + e.getKey().getTaxiId() + "): " +
                        //        "The other one wins, I step back.");
                    }else{
                        //I win
                        e.getValue().onNext(TaxiCoordinationReply.newBuilder().setOk(false).build());
                        e.getValue().onCompleted();
                        //debug("(taxi " + thisTaxi.getId() + " received from taxi " + e.getKey().getTaxiId() + "): " +
                        //        "I win!");
                    }

                    stop = tmp || stop;
                }

                boolean ret = false;
                if(!stop) {

                    ret = askOtherTaxisAboutARide(currentRideRequest);
                    //when ret=true, it means I've been chosen to take care of this ride request
                }

                if(ret){
                    debug2("T" + thisTaxi.getId()+ " - I handle request number " + currentRequestBeingProcessed);
                    double toLower1 = 0D;
                    double toLower2 = 0D;
                    replyYesToAllPendingRideElectionRequests();
                    synchronized (thisTaxi.stateLock) {
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

                        //2) update my coordinates to the one of the final location of the ride
                        thisTaxi.setCurrX(rrmCurrentRequestBeingProcessed.getArrivingX());
                        thisTaxi.setCurrY(rrmCurrentRequestBeingProcessed.getArrivingY());

                        //3) change my district from the current one to the one toward which I'm heading
                        District newDistrict = SmartCity.getDistrict(
                                currentRideRequest.getArrivingX(), currentRideRequest.getArrivingY());

                        subscribeToADistrictTopic(newDistrict);

                        //4) lower the battery value
                        toLower1 = (int) SmartCity.distance(thisTaxi.getCurrX(), thisTaxi.getCurrY(),
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
                        debug("starting the ride...");
                        Thread.sleep(5000);
                        debug("ride finished!");
                    } catch (InterruptedException e) {e.printStackTrace();}


                }else{
                    synchronized (thisTaxi.stateLock) {
                        thisTaxi.setState(Commons.IDLE);
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

    private void debug2(String msg){
        if(DEBUG_LOCAL2) System.out.println(" " + msg);
    }



    private void subscribeToADistrictTopic(District d){



        String topic = "seta/smartcity/rides/" + d.toString().toLowerCase();
        try{

            //if the client was already subscribed to a district, it must now unsubscribe from them
            client.unsubscribe("seta/smartcity/rides/+");   //TODO: lancia errore se non è registrato? devo mettere un array?

            debug(thisTaxi.getId() + " subscribing to district " + d.toString().toLowerCase());
            client.subscribe(topic, qos);
            debug(thisTaxi.getId() + " subscribed to district " + d.toString().toLowerCase());

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
        //debug("ok, subscribed!");

        //now, the Taxi can notify Seta that he is now present in this district.
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
            //add the request only if we know it's not already been satisfied OR it is not already present
            if(thisTaxi.satisfiedRides.get(SmartCity.getDistrict(rrm.getStartingX(), rrm.getStartingY())) >
                    rrm.getId()){
                debug("NO! I'm NOT adding request " + rrm.getId() + ", because the last satisfied request is " + thisTaxi.satisfiedRides.get(SmartCity.getDistrict(rrm.getStartingX(), rrm.getStartingY())));
                return;
            }
            for(RideRequestMessage req : incomingRequests){
                if(req.getId() == rrm.getId()){
                    debug("NO! I'm NOT adding request " + rrm.getId() + ", for the other reason");
                    return;
                }
            }
            debug("Taxi " + thisTaxi.getId() + " added request number " + rrm.getId());
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
        if(ret!=null) debug("The request taken from the queue is " + ret.getId());
        return ret;
    }

    private boolean askOtherTaxisAboutARide(RideRequestMessage currentRideRequest){
        //debug("(Taxi " + thisTaxi.getId() + "): starting to send election messages to other taxis");
        synchronized (thisTaxi.otherTaxisLock) {
            otherTaxisInThisElection = new HashMap<>(thisTaxi.getOtherTaxis());
        }
        //debug("the number of other taxis is " + otherTaxisInThisElection.size());
        synchronized(election_lock){
            for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : otherTaxisInThisElection.entrySet()){
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
                    //debug("(I'm taxi " + thisTaxi.getId() + ") ...but taxi " + entry.getValue().getId() + " told me" +
                    //        " to step back :(");
                    return false;
                }
                //debug("(I'm taxi " + thisTaxi.getId() + ") ...and taxi " + entry.getValue().getId() + " told me" +
                //        " ok! :)");
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








    public void addPendingRideElectionRequest(TaxiCoordinationRequest k, StreamObserver<TaxiCoordinationReply> v){
        synchronized (pendingRideElectionRequests_lock) {
            pendingRideElectionRequests.put(k, v);
        }
    }

    public HashMap<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> getPendingRideElectionRequestForSpecificRequest(int reqId){
        HashMap<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> ret = new HashMap<>();
        synchronized (pendingRideElectionRequests_lock) {
            for (Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e :
                    pendingRideElectionRequests.entrySet()) {
                if (e.getKey().getIdRideRequest() == reqId) {
                    ret.put(e.getKey(), e.getValue());
                }
            }

            //now we delete these pending requests from the hashmap of pending requests
            for (Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e :
                    ret.entrySet()) {
                pendingRideElectionRequests.remove(e.getKey());
            }
        }
        return ret;

    }


    //when a taxi starts riding, it can reply "yes" to all the pending requests it queued.
    public void replyYesToAllPendingRideElectionRequests(){
        synchronized (pendingRideElectionRequests_lock) {
            for (Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e :
                    pendingRideElectionRequests.entrySet()) {

                e.getValue().onNext(TaxiCoordinationReply.newBuilder().setOk(true).build());
                e.getValue().onCompleted();
                debug2("(taxi " + thisTaxi.getId() + " is now riding, so responds \"ok\" to taxi " +
                        e.getKey().getTaxiId() +
                        " about the request number " + e.getKey().getIdRideRequest());
            }

            //now we delete all the pending requests from the hashmap of pending requests
            pendingRideElectionRequests.clear();
        }
    }

    /*public TaxiCoordinationRequest getMinimumPendingRideElectionRequestsInput(){
        int min = Integer.MAX_VALUE;
        TaxiCoordinationRequest ret = null;
        for(Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e :
                pendingRideElectionRequests.entrySet()){
            if(e.getKey().getIdRideRequest() < min){
                min = e.getKey().getIdRideRequest();
                ret = e.getKey();
            }
        }
        return ret;
    }

    //this also deletes the pending request from the hashmap
    public StreamObserver<TaxiCoordinationReply> getMinimumPendingRideElectionRequestsStreamObserver(){
        int min = Integer.MAX_VALUE;
        StreamObserver<TaxiCoordinationReply> ret = null;
        TaxiCoordinationRequest toRemove = null;
        for(Map.Entry<TaxiCoordinationRequest, StreamObserver<TaxiCoordinationReply>> e :
                pendingRideElectionRequests.entrySet()){
            if(e.getKey().getIdRideRequest() < min){
                min = e.getKey().getIdRideRequest();
                ret = e.getValue();
                toRemove = e.getKey();
            }
        }
        pendingRideElectionRequests.remove(toRemove);
        return ret;
    }*/








    //false = I win (I will maybe take care of this request)
    //true = the other wins (you will not, for sure, take care of this request)
    public boolean compareTaxis(TaxiCoordinationRequest other){
        Taxi me = thisTaxi;

        //debug("me = (" + me.getCurrX() + "," + me.getCurrY() + ")" + " - " + me.getBatteryLevel() + "% - id = " + me.getId() +
        //        "ot = (" + other.getX() + "," + other.getY() + ")" + " - " + other.getBatteryLevel() + "% - id = " + other.getTaxiId());

        //*the Taxi must have the minimum distance from the starting point of
        //the ride*
        double myDistance = SmartCity.distance(me.getCurrX(), me.getCurrY(),
                rrmCurrentRequestBeingProcessed.getStartingX(), rrmCurrentRequestBeingProcessed.getStartingY());
        double otherDistance = SmartCity.distance(other.getX(), other.getY(),
                rrmCurrentRequestBeingProcessed.getStartingX(), rrmCurrentRequestBeingProcessed.getStartingY());
        if(myDistance - otherDistance < 0){
            //debug("I WIN");
            return false;
        }else if(myDistance - otherDistance > 0.01){
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
