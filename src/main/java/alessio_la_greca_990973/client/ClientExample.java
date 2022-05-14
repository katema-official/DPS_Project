package alessio_la_greca_990973.client;

import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class ClientExample {
    public static void main(String[] args){
        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        String postPath = "/taxi/join";
        TaxiServerRepresentation word = new TaxiServerRepresentation(1,"localhost", 47000);
        System.out.println("sending post request...");
        clientResponse = postRequest(client,serverAddress+postPath,word);
        System.out.println(clientResponse.toString());
    }

    public static ClientResponse postRequest(Client client, String url, TaxiServerRepresentation t){
        WebResource webResource = client.resource(url);
        String input = new Gson().toJson(t);
        try {
            return webResource.type("application/json").post(ClientResponse.class, input);
        } catch (ClientHandlerException e) {
            System.out.println("Server not available");
            return null;
        }
    }
}
