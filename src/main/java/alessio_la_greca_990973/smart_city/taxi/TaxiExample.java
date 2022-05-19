package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import alessio_la_greca_990973.server.fortaxi.datas.statistics.TaxiStatisticsPacket;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaxiExample {
    public static void main(String[] args){

        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        //add taxi
        TaxiServerRepresentation taxi = new TaxiServerRepresentation(0, "localhost", 45800);
        clientResponse = postRequestJoin(client, serverAddress + "/taxi/join", taxi);
        System.out.println(clientResponse.toString());

        //add statistics
        List<Double> pollutions = Arrays.asList(10D,20D,30D);
        TaxiStatisticsPacket packet = new TaxiStatisticsPacket(10, 2, pollutions, 0, 100D, 60);
        clientResponse = postRequestAppend(client, serverAddress + "/taxi/append", packet);
        System.out.println(clientResponse.toString());

        pollutions = Arrays.asList(8D,12D,16D);
        packet = new TaxiStatisticsPacket(8, 1, pollutions, 0, 120D, 55);
        clientResponse = postRequestAppend(client, serverAddress + "/taxi/append", packet);
        System.out.println(clientResponse.toString());



        //add taxi
        taxi = new TaxiServerRepresentation(1, "localhost", 45801);
        clientResponse = postRequestJoin(client, serverAddress + "/taxi/join", taxi);
        System.out.println(clientResponse.toString());

        //add statistics
        pollutions = Arrays.asList(2D,4D,6D);
        packet = new TaxiStatisticsPacket(5, 1, pollutions, 1, 105D, 80);
        clientResponse = postRequestAppend(client, serverAddress + "/taxi/append", packet);
        System.out.println(clientResponse.toString());

        pollutions = Arrays.asList(1D,6D,8D);
        packet = new TaxiStatisticsPacket(2, 1, pollutions, 1, 140D, 75);
        clientResponse = postRequestAppend(client, serverAddress + "/taxi/append", packet);
        System.out.println(clientResponse.toString());

    }

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

    public static ClientResponse postRequestAppend(Client client, String url, TaxiStatisticsPacket packet){
        WebResource webResource = client.resource(url);
        String input = new Gson().toJson(packet);
        System.out.println("url = " + url + ", json = " + input);
        try {
            return webResource.type("application/json").post(ClientResponse.class, input);
        } catch (ClientHandlerException e) {
            System.out.println("Server non disponibile");
            return null;
        }
    }

}
