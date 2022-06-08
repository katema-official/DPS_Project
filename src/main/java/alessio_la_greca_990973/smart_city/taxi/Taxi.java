package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiReplyToJoin;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.taxi.pollution_simulator.PollutionSimulatorThread;
import alessio_la_greca_990973.smart_city.taxi.rpcservices.MiscTaxiServiceImpl;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryListener;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryManager;
import alessio_la_greca_990973.smart_city.taxi.threads.IdleThread;
import alessio_la_greca_990973.smart_city.taxi.threads.StatisticsThread;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import taxis.service.MiscTaxiServiceGrpc;
import taxis.service.MiscTaxiServiceGrpc.*;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Taxi implements Runnable{

    private int ID;
    private String host;
    private int batteryLevel;

    private int currX;
    private int currY;

    private int state;
    public Object stateLock;

    //each taxi must take know the other taxis: in particular, he wants to know, about each of them:
    //-id
    //-hostname
    //-port
    //-x position
    //-y position
    private HashMap<Integer, TaxiTaxiRepresentation> otherTaxis;
    public Object otherTaxisLock;



    private Server taxiService;



    public Object alertBatteryRecharge;
    private BatteryManager batteryManager;



    private PendingRechargeRequestQueue queue;
    private BatteryListener batteryListener;



    public Object rechargeComplete_lock;




    public boolean explicitRechargeRequest;
    private Object explicitRechargeRequest_lock;

    public Object incomingRequests_lock;


    private StatisticsThread st;



    private Client client;
    private String serverAddress;


    public Taxi(int ID, String host) {
        this.ID = ID;
        this.host = host;

        //Moreover, the initial battery level of each taxi is equal to 100%.
        this.batteryLevel = 100;

        otherTaxis = new HashMap<>();
        otherTaxisLock = new Object();
        alertBatteryRecharge = new Object();
        stateLock = new Object();
        incomingRequests_lock = new Object();
        queue = new PendingRechargeRequestQueue();
        batteryListener = new BatteryListener(this, queue);
        batteryManager = new BatteryManager(this, batteryListener);

        rechargeComplete_lock = new Object();
        setState(Commons.INITIALIZING);

        explicitRechargeRequest = false;
        explicitRechargeRequest_lock = new Object();

    }

    public void run() {
        /*Once it is launched, the Taxi process must register itself to the
        system through the Administrator Server*/

        client = Client.create();
        serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        //add taxi
        TaxiServerRepresentation taxi = new TaxiServerRepresentation(ID, host, getPort());
        clientResponse = postRequestJoin(client, serverAddress + "/taxi/join", taxi);
        System.out.println(clientResponse.toString());
        if(clientResponse.getStatus() == 200) {
            /*If its insertion is successful (i.e., there are no other
            taxis with the same ID), the Taxi receives from the Administrator Server:
            - its starting position in the smart city (one of the four recharge stations
            distributed among the districts)
            - the list of the other taxis already present in the smart city*/
            TaxiReplyToJoin reply = clientResponse.getEntity(TaxiReplyToJoin.class);
            currX = reply.getStartingX();
            currY = reply.getStartingY();

            List<TaxiServerRepresentation> taxis = reply.getCurrentTaxis();

            /*Once the Taxi receives this information, it starts acquiring data from the
            pollution sensor*/
            PollutionSimulatorThread pollutionsThread = new PollutionSimulatorThread(this);
            Thread t_pollutions = new Thread(pollutionsThread);
            t_pollutions.start();

            //thread that handles the sending of the statistics to the server
            st = new StatisticsThread(this, pollutionsThread);

            //(I add this) before presenting myself to the other taxis, I must open my gRPC service to them,
            //so that they and future taxis will be able to contact me and ask whatever they want
            IdleThread it = new IdleThread(this, st);
            taxiService = ServerBuilder.forPort(getPort()).addService(new MiscTaxiServiceImpl(this, batteryListener, it)).build();
            try {
                taxiService.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            /*Then, if there are other taxis in the smart city, the taxi
            presents itself to the other taxis by sending them its position in the grid*/
            if (taxis != null) {
                for (TaxiServerRepresentation t : taxis) {
                    TaxiTaxiRepresentation ttr = new TaxiTaxiRepresentation(t.getId(), t.getHostname(), t.getListeningPort(), -1, -1);
                    OldTaxiPresentation oldTaxi = synchronousCallWelcome(t.getHostname(), t.getListeningPort());
                    ttr.setCurrX(oldTaxi.getCurrX());
                    ttr.setCurrY(oldTaxi.getCurrY());
                    //here the lock (to modify the otherTaxis data structure) is not needed, since there is no
                    //concurrency at this point.
                    otherTaxis.put(ttr.getId(), ttr);
                }
            }

            //thread that handles recharge requests
            Thread t2 = new Thread(batteryManager);
            t2.start();

            Thread t3 = new Thread(st);
            t3.start();

            /*Finally, the taxi subscribes to the MQTT topic of its district*/
            Thread t1 = new Thread(it);
            t1.start();

            synchronized (TaxiMain.taxiMain_lock){
                TaxiMain.ok = 1;
                TaxiMain.taxiMain_lock.notify();
            }

            try {
                taxiService.awaitTermination();
            } catch (InterruptedException e) {throw new RuntimeException(e);}

        }else{
            System.out.println("That taxi was already present. Try another id please.");
            synchronized (TaxiMain.taxiMain_lock){
                TaxiMain.ok = 0;
                TaxiMain.taxiMain_lock.notify();
            }
        }
    }

    public int getPort(){
        return 49152 + ID;
    }

    //##################################################################################################
    //#                                       REST requests                                            #
    //##################################################################################################
    public static ClientResponse postRequestJoin(Client client, String url, TaxiServerRepresentation taxi){
        WebResource webResource = client.resource(url);
        String input = new Gson().toJson(taxi);
        try {
            return webResource.type("application/json").post(ClientResponse.class, input);
        } catch (ClientHandlerException e) {
            System.out.println("Server non disponibile");
            return null;
        }
    }

    public static ClientResponse deleteRequestLeave(Client client, String url, int taxiId){
        WebResource webResource = client.resource(url + "?id=" + taxiId);
        try {
            return webResource.delete(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server non disponibile");
            return null;
        }
    }



    //to contact older taxis when I'm added to the city
    private OldTaxiPresentation synchronousCallWelcome(String host, int port){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(host + ":" + port).usePlaintext().build();
        MiscTaxiServiceBlockingStub stub = MiscTaxiServiceGrpc.newBlockingStub(channel);

        NewTaxiPresentation me = NewTaxiPresentation.newBuilder().setId(ID).setHostname(host).setPort(getPort())
                .setCurrX(currX).setCurrY(currY).build();

        OldTaxiPresentation old = stub.welcomeImANewTaxi(me);
        channel.shutdown();
        return old;
    }

    public void shutdownTaxiServer(){
        setState(Commons.EXITING);

        //we have to wake up the taxi from possible waiting states:
        //1) the taxi could be waiting for a request
        synchronized (incomingRequests_lock) {
            incomingRequests_lock.notify();
        }

        //2) the taxi could be waiting to recharge
        synchronized (alertBatteryRecharge) {
            alertBatteryRecharge.notify();
        }
        synchronized(batteryManager.canRecharge){
            batteryManager.canRecharge.notify();
        }


        HashMap<Integer, TaxiTaxiRepresentation> otherTaxisToSayBye;
        //we have to tell to every other taxi that we are exiting, and that they must forget about us
        synchronized (otherTaxisLock) {
            otherTaxisToSayBye = new HashMap<>(getOtherTaxis());
        }

        for(Map.Entry<Integer, TaxiTaxiRepresentation> entry : otherTaxisToSayBye.entrySet()) {
            String host = entry.getValue().getHostname();
            int port = entry.getValue().getListeningPort();
            ManagedChannel channel = ManagedChannelBuilder.forTarget(host + ":" + port).usePlaintext().build();
            MiscTaxiServiceBlockingStub stub = MiscTaxiServiceGrpc.newBlockingStub(channel);

            ExitingAnnouncement ea = ExitingAnnouncement.newBuilder().setTaxiId(getId()).build();

            ExitingOk reply = stub.iAmExiting(ea);
            if(reply.getOk() != true){
                System.out.println("Errore nel comunicare ad un altro taxi che sto uscendo");
            }
            channel.shutdown();
        }

        //now we can tell the server that we are going away
        ClientResponse response = deleteRequestLeave(client, serverAddress + "/taxi/leave", getId());

        //then, to make sure all other taxis receive responses from this one before it exits,
        //we wait a bit of time, like five seconds
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {throw new RuntimeException(e);}


        taxiService.shutdownNow();
    }

    public HashMap<Integer, TaxiTaxiRepresentation> getOtherTaxis() {
        return otherTaxis;
    }

    public void setOtherTaxis(HashMap<Integer, TaxiTaxiRepresentation> otherTaxis) {
        this.otherTaxis = otherTaxis;
    }

    public int getCurrX() {
        return currX;
    }

    public void setCurrX(int currX) {
        this.currX = currX;
    }

    public int getCurrY() {
        return currY;
    }

    public void setCurrY(int currY) {
        this.currY = currY;
    }

    public int getId(){ return ID;}

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public void subtractPercentageFromBatteryLevel(int amount){
        this.batteryLevel -= amount;
    }

    public PendingRechargeRequestQueue getQueue() {
        return queue;
    }

    public BatteryManager getBatteryManager() {
        return batteryManager;
    }

    public BatteryListener getBatteryListener() {
        return batteryListener;
    }

    public Server getTaxiService() {
        return taxiService;
    }

    public int getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    public boolean setState(int state) {
        //once the state has been set to EXITING, it cannot be further modified
        //true = the state has been modified
        //false = the state has not been modified (the state is exiting)
        synchronized (stateLock) {
            if(this.state != Commons.EXITING) {
                this.state = state;
                return true;
            }
        }
        return false;
    }



    //USED FOR MANUAL RECHARGE
    public void setExplicitRechargeRequest(boolean req){
        synchronized (explicitRechargeRequest_lock) {
            explicitRechargeRequest = req;
        }
    }

    public boolean getExplicitRechargeRequest(){
        synchronized (explicitRechargeRequest_lock){
            return explicitRechargeRequest;
        }
    }
}
