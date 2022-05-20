package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiReplyToJoin;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class Taxi {

    private static boolean DEBUG_LOCAL = true;

    private int ID;
    private String host;
    private int batteryLevel;

    public Taxi(int ID, String host) {
        this.ID = ID;
        this.host = host;
        this.batteryLevel = 100;
    }

    public void init(){
        Client client = Client.create();
        String serverAddress = "http://localhost:1337";
        ClientResponse clientResponse = null;

        //add taxi
        TaxiServerRepresentation taxi = new TaxiServerRepresentation(ID, host, getPort());
        clientResponse = postRequestJoin(client, serverAddress + "/taxi/join", taxi);
        System.out.println(clientResponse.toString());
        if(clientResponse.getStatus() == 200) {
            TaxiReplyToJoin reply = clientResponse.getEntity(TaxiReplyToJoin.class);

            debug("Your taxi is now in the city. Here are some infos:\n" +
                    reply.getStartingX() + "\n" +
                    reply.getStartingY() + "\n");
            List<TaxiServerRepresentation> otherTaxis = reply.getCurrentTaxis();
            if (otherTaxis != null) {
                for (TaxiServerRepresentation t : otherTaxis) {
                    debug(t.getId() + "\n" + t.getListeningPort() + "\n");
                }
            }

            //aggiungi le informazioni dei taxi a una struttura dati, dopo aver anche chiamato
            //con grpc gli altri taxi per sapere la loro posizione/distretto.

        }else{
            System.out.println("That taxi was already present. Try another id please.");
        }
    }

    private int getPort(){
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



}
