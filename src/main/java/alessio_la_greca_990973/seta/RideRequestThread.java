package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass.RideRequestMessage;

import java.sql.Timestamp;
import java.util.Random;

public class RideRequestThread implements Runnable{

    private static boolean DEBUG_LOCAL = true;

    private int requestDelay = 5000;    //so it can be changed if needed
    private MqttClient client;
    private int qos;

    public RideRequestThread(){
        //the request thread must register as publishers on the MQTT broker
        client = null;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        String topic1 = "seta/smartcity/rides/district1";
        String topic2 = "seta/smartcity/rides/district2";
        String topic3 = "seta/smartcity/rides/district3";
        String topic4 = "seta/smartcity/rides/district4";
        qos = 2;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            // Connect the client
            System.out.println(clientId + " RideRequestThread connecting to broker " + broker);
            client.connect(connOpts);
            System.out.println(clientId + " Connected!");

            //if (client.isConnected())
            //    client.disconnect();
            //System.out.println("Publisher " + clientId + " disconnected");

        } catch (MqttException me ) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
    }

    @Override
    public void run() {

        while(true) {
            //this thread must generate, each five seconds, in a random moment, a ride request. To do so,
            //we generate a random number between 0 and 4999, sleep for that time, generate the request,
            //sleep for 5000 - the previously generated time and then repeat.
            Random rand = new Random();
            int millis = rand.nextInt(requestDelay);
            try {
                Thread.sleep(millis);

                int ID = Seta.generateNewRideRequestID();

                int startingX;
                int startingY;
                int arrivingX;
                int arrivingY;
                do {
                    startingX = SmartCity.generateRandomXInsideSmartCity();
                    startingY = SmartCity.generateRandomYInsideSmartCity();
                    arrivingX = SmartCity.generateRandomXInsideSmartCity();
                    arrivingY = SmartCity.generateRandomYInsideSmartCity();
                }while(startingX == arrivingX && startingY == arrivingY);

                //let's find out the topic on which we have to publish the request
                District d = SmartCity.getDistrict(startingX, startingY);
                String last = d.toString().toLowerCase();
                String topic = "seta/smartcity/rides/" + last;

                //let's now generate the request...
                RideRequestMessage rrm = RideRequestMessage.newBuilder()
                        .setId(ID)
                        .setStartingX(startingX)
                        .setStartingY(startingY)
                        .setArrivingX(arrivingX)
                        .setArrivingY(arrivingY).build();

                //...save it in the pending requests...
                Seta.addPendingRequest(d, rrm);

                //...and publish it on the MQTT broker
                MqttMessage message = new MqttMessage(rrm.toByteArray());

                // Set the QoS on the Message
                message.setQos(qos);
                System.out.println("Publishing request number " + ID + "...");
                client.publish(topic, message);
                System.out.println("Request published!");


                Thread.sleep(requestDelay - millis);


                debug("Cycle ended after publishing requst number " + ID + ". Restarting...");


            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (MqttPersistenceException e) {
                throw new RuntimeException(e);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void debug(String message){
        if(Commons.DEBUG_GLOBAL && RideRequestThread.DEBUG_LOCAL){
            System.out.println("message");
        }
    }
}
