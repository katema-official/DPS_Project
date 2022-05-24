package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiReplyToJoin;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import alessio_la_greca_990973.smart_city.taxi.pollution_simulator.PollutionSimulatorThread;
import alessio_la_greca_990973.smart_city.taxi.rpcservices.WelcomeServiceImpl;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryListener;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryManager;
import alessio_la_greca_990973.smart_city.taxi.threads.IdleThread;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import taxis.welcome.WelcomeServiceGrpc;
import taxis.welcome.WelcomeServiceGrpc.WelcomeServiceBlockingStub;
import taxis.welcome.WelcomeServiceGrpc.WelcomeServiceImplBase;
import taxis.welcome.WelcomeTaxiService.*;

import java.io.IOException;
import java.util.HashMap;
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


    public Taxi(int ID, String host) {
        this.ID = ID;
        this.host = host;

        //Moreover, the initial battery level of each taxi is equal to 100%.
        this.batteryLevel = 100;

        otherTaxis = new HashMap<>();
        otherTaxisLock = new Object();
        termination = new Object();
        alertBatteryRecharge = new Object();
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


            //(I add this) now that I have the infos about the other taxis, I can open my gRPC service to other taxis,
            //so that future taxis will be able to contact me and ask me my position (they will also tell me their initial position)
            taxiService = ServerBuilder.forPort(getPort()).addService(new WelcomeServiceImpl(this)).build();
            taxiService.start();
            //taxiService.awaitTermination();


            /*Finally, the taxi subscribes to the MQTT topic of its district*/
            IdleThread it = new IdleThread(this);
            Thread t1 = new Thread(it);
            t1.start();

            //thread that handles recharge requests
            BatteryManager bm = new BatteryManager(this);
            Thread t2 = new Thread(bm);
            t2.start();



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
        WelcomeServiceBlockingStub stub = WelcomeServiceGrpc.newBlockingStub(channel);

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

}
