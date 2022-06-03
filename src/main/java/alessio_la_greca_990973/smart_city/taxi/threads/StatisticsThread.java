package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import alessio_la_greca_990973.server.fortaxi.datas.statistics.TaxiStatisticsPacket;
import alessio_la_greca_990973.simulator.Measurement;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.pollution_simulator.PollutionSimulatorThread;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.util.ArrayList;

public class StatisticsThread implements Runnable{

    private boolean DEBUG_LOCAL = true;
    private Taxi thisTaxi;
    private PollutionSimulatorThread pollutionSimulator;


    private double currentlyTraveledKilometers;
    private int satisfiedRides;
    private Object stats_lock;

    public StatisticsThread(Taxi thisTaxi, PollutionSimulatorThread pollutionSimulator){
        this.thisTaxi = thisTaxi;
        this.pollutionSimulator = pollutionSimulator;
        currentlyTraveledKilometers = 0D;
        satisfiedRides = 0;
        stats_lock = new Object();
    }

    @Override
    public void run() {
        while(true){        //TODO: !taxiDeveTerminare
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            /*Every 15 seconds, each Taxi has to compute and communicate to the administrator server
            the following local statistics (computed during this interval of 15 seconds):
            -The number of kilometers traveled to accomplish the rides of the taxi
            -The number of rides accomplished by the taxi
            -The list of the averages of the pollution levels measurements*/

            /*Once the local statistics have been computed, the Taxi sends them to the
             Administrator Server associated with
             -The ID of the Taxi
             -The timestamp in which the local statistics were computed
             -The current battery level of the Taxi*/
            sendCurrentStatisticsToServer();
            debug("STATISTICS SENT!");
        }
    }


    public void sendCurrentStatisticsToServer(){

        double km = getCurrentlyTraveledKilometers();
        debug("curret kilometers I'm sending: " + km);
        ArrayList<Double> pollutions = pollutionSimulator.getMeanMeasurements();
        for(Double p : pollutions){
            System.out.println("pollution = " + p);
        }

        TaxiStatisticsPacket packet = new TaxiStatisticsPacket(km, getSatisfiedRides(),
                pollutions, thisTaxi.getId(), System.currentTimeMillis(),
                thisTaxi.getBatteryLevel());

        //send to the server
        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        //add taxi
        clientResponse = postStatistics(client, serverAddress + "/taxi/append", packet);
        System.out.println(clientResponse.toString());
        if(clientResponse.getStatus() == 200) {

        }else{
            debug("ERROR in sending statistics to server (from taxi " + thisTaxi.getId() + ")");
        }

    }

    public static ClientResponse postStatistics(Client client, String url, TaxiStatisticsPacket taxi){
        WebResource webResource = client.resource(url);
        String input = new Gson().toJson(taxi);
        try {
            return webResource.type("application/json").post(ClientResponse.class, input);
        } catch (ClientHandlerException e) {
            System.out.println("Server non disponibile");
            return null;
        }
    }



    public void addKilometers(double km){
        synchronized (stats_lock){
            currentlyTraveledKilometers += km;
        }
    }

    public void addRide(){
        synchronized (stats_lock) {
            satisfiedRides += 1;
        }
    }



    private double getCurrentlyTraveledKilometers(){
        synchronized (stats_lock) {
            double tmp = currentlyTraveledKilometers;
            currentlyTraveledKilometers = 0D;
            return tmp;
        }
    }
    private int getSatisfiedRides(){
        synchronized (stats_lock) {
            int tmp = satisfiedRides;
            satisfiedRides = 0;
            return tmp;
        }
    }



    private void debug(String message){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + message);
        }
    }

}
