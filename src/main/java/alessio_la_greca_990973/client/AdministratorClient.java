package alessio_la_greca_990973.client;

import alessio_la_greca_990973.server.forclient.TaxiStatistic;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;

public class AdministratorClient {
    public static void main(String args[]){

        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        System.out.println("Welcome, administrator client! Type a command to perform a request (type help for the list of commands)");
        String line = "";
        BufferedReader reader;
        while(true){
            reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                line = reader.readLine();
                System.out.println("line = " + line);
            } catch (IOException e) {e.printStackTrace();}

            if(line.equals("help")) {
                printHelp();
            }else{
                if(!line.equals("exit")){
                    String[] split = line.split(" ");
                    if(split[0].equals("taxis")){
                        printTaxis(client);
                    }else if (split[0].equals("statistics") && split.length == 3){
                        Integer id = tryParseInt(split[1]);
                        Integer n = tryParseInt(split[2]);
                        if(id == null || n == null) {
                            System.out.println("Error: you must specify an \"id\" and an \"n\" to get the statistics. Type \"help\" for more informations");
                        }else{
                            printStatistics(client, id, n);
                        }

                    }else if(split[0].equals("timestamps") && split.length == 3){
                        Double t1 = tryParseDouble(split[1]);
                        Double t2 = tryParseDouble(split[2]);
                        if(t1 == null || t2 == null){
                            System.out.println("Error: you must specify a minimum timestamp \"t1\" and a maximum timestamp \"t2\" to get the statistics. Type \"help\" for more informations");
                        }else{
                            printTimestamps(client, t1, t2);
                        }
                    }
                }else{
                    break;
                }
            }

        }

    }

    public static Integer tryParseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double tryParseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void printHelp(){
        System.out.println("Here are the commands you can type: \n" +
                "taxis - query the taxis currently present in the smart city \n" +
                "statistics id n - get the average of the last n statistics of the taxi with id \"id\" \n" +
                "timestamps t1 t2 - get the average of all the statistics collected that happened between t1 and t2 \n" +
                "exit - terminate this client");
    }

    private static ClientResponse getTaxis(Client client){
        String url = "http://localhost:1337/client/taxis";
        ClientResponse res = getRequest(client, url);
        return res;
    }

    private static ClientResponse getStatistics(Client client, int id, int n){
        String url = "http://localhost:1337/client/stats/" + id + "/" + n;
        ClientResponse res = getRequest(client, url);
        return res;
    }

    private static ClientResponse getTimestamps(Client client, double t1, double t2){
        String url = "http://localhost:1337/client/timestamps/" + t1 + "/" + t2;
        ClientResponse res = getRequest(client, url);
        return res;
    }

    private static ClientResponse getRequest(Client client, String url){
        WebResource webResource = client.resource(url);
        try {
            return webResource.type("application/json").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server non disponibile");
            return null;
        }
    }






    private static void printTaxis(Client client){
        ClientResponse clientResponse = getTaxis(client);
        String json = clientResponse.getEntity(String.class);
        System.out.println("taxis: \n" + json);
        Type listType = new TypeToken<ArrayList<TaxiServerRepresentation>>() {}.getType();
        ArrayList<TaxiServerRepresentation> taxis = new Gson().fromJson(json, listType);
        for(TaxiServerRepresentation taxi: taxis){
            System.out.println("taxi: " + taxi.getId() + " " + taxi.getHostname() + " " + taxi.getListeningPort());
        }
    }

    private static void printStatistics(Client client, int id, int n){
        ClientResponse clientResponse = getStatistics(client, id, n);
        String json = clientResponse.getEntity(String.class);
        System.out.println("statistics: \n" + json);
    }

    private static void printTimestamps(Client client, double t1, double t2){
        ClientResponse clientResponse = getTimestamps(client, t1, t2);
        String json = clientResponse.getEntity(String.class);
        System.out.println("timestamps: \n" + json);
    }

}
