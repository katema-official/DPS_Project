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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class Taxi {

    private static boolean DEBUG_LOCAL = true;

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
    private Object termination;



    public Object alertBatteryRecharge;
    private BatteryManager batteryManager;



    private PendingRechargeRequestQueue queue;
    private BatteryListener batteryListener;



    public Object rechargeComplete_lock;


    //map that associates to each district the highest (and last) satisfied ride
    //by this taxi
    public HashMap<District, Integer> satisfiedRides;




    public boolean explicitRechargeRequest;
    private Object explicitRechargeRequest_lock;

    public Taxi(int ID, String host) {
        this.ID = ID;
        this.host = host;

        //Moreover, the initial battery level of each taxi is equal to 100%.
        this.batteryLevel = 100;

        otherTaxis = new HashMap<>();
        otherTaxisLock = new Object();
        termination = new Object();
        alertBatteryRecharge = new Object();
        stateLock = new Object();
        queue = new PendingRechargeRequestQueue();
        batteryListener = new BatteryListener(this, queue);
        batteryManager = new BatteryManager(this, batteryListener);

        rechargeComplete_lock = new Object();
        satisfiedRides = new HashMap<District, Integer>();
        satisfiedRides.put(District.DISTRICT1, -1);
        satisfiedRides.put(District.DISTRICT2, -1);
        satisfiedRides.put(District.DISTRICT3, -1);
        satisfiedRides.put(District.DISTRICT4, -1);
        setState(Commons.INITIALIZING);

        explicitRechargeRequest = false;
        explicitRechargeRequest_lock = new Object();
    }

    public void init() throws IOException {
        /*Once it is launched, the Taxi process must register itself to the
        system through the Administrator Server*/

        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
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

            //TODO: remove
            currX = 0;
            currY = 0;

            List<TaxiServerRepresentation> taxis = reply.getCurrentTaxis();

            /*Once the Taxi receives this information, it starts acquiring data from the
            pollution sensor*/
            PollutionSimulatorThread pollutionsThread = new PollutionSimulatorThread(this);
            Thread t_pollutions = new Thread(pollutionsThread);
            t_pollutions.start();

            /*Then, if there are other taxis in the smart city, the taxi
            presents itself to the other taxis by sending them its position in the grid*/
            if (taxis != null) {
                for (TaxiServerRepresentation t : taxis) {
                    debug("Contacting port " + t.getListeningPort());
                    TaxiTaxiRepresentation ttr = new TaxiTaxiRepresentation(t.getId(), t.getHostname(), t.getListeningPort(), -1, -1);
                    debug(t.getHostname() + ":" + t.getListeningPort());
                    OldTaxiPresentation oldTaxi = synchronousCallWelcome(t.getHostname(), t.getListeningPort());
                    ttr.setCurrX(oldTaxi.getCurrX());
                    ttr.setCurrY(oldTaxi.getCurrY());
                    //here the lock (to modify the otherTaxis data structure) is not needed, since there is no
                    //concurrency at this point.
                    otherTaxis.put(ttr.getId(), ttr);
                }
            }

            //thread that handles the sneding of the statistics to the server
            StatisticsThread st = new StatisticsThread(this, pollutionsThread);

            //(I add this) now that I have the infos about the other taxis, I can open my gRPC service to other taxis,
            //so that future taxis will be able to contact me and ask me my position (they will also tell me their initial position)
            IdleThread it = new IdleThread(this, st);

            taxiService = ServerBuilder.forPort(getPort()).addService(new MiscTaxiServiceImpl(this, batteryListener, it)).build();
            taxiService.start();
            //taxiService.awaitTermination();

            //thread that handles recharge requests
            Thread t2 = new Thread(batteryManager);
            t2.start();

            /*Finally, the taxi subscribes to the MQTT topic of its district*/
            Thread t1 = new Thread(it);
            t1.start();



            //-----------------------------debug-------------------------------
            debug("Your taxi is now in the city. Here are some infos:\n" +
                    currX + "\n" +
                    currY + "\n");
            taxis = reply.getCurrentTaxis();
            if (taxis != null) {
                for (Map.Entry<Integer, TaxiTaxiRepresentation> entry : otherTaxis.entrySet()) {
                    debug(entry.getValue().getId() + "\n" + entry.getValue().getListeningPort() + "\n" +
                            "(" + entry.getValue().getCurrX() + "," + entry.getValue().getCurrY() + ")");
                }
            }
            //----------------------------end debug-----------------------------


            try {
                taxiService.awaitTermination();
                System.out.println("TERMINATED!");
            } catch (InterruptedException e) {throw new RuntimeException(e);}

        }else{
            System.out.println("That taxi was already present. Try another id please.");
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


    private void debug(String message){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + message);
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

    public void setState(int state) {
        synchronized (stateLock) {
            this.state = state;
        }
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
