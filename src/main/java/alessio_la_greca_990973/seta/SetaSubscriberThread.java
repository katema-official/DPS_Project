package alessio_la_greca_990973.seta;

import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass.*;

import java.sql.Timestamp;

public class SetaSubscriberThread implements Runnable{

    public SetaSubscriberThread(){
        //this are the topics on which seta subscribes. Here, it will receive
        //from taxis messages that say either they are now in a particular
        //district or the acks about a particular ride
        String topicMessagesAcks = "seta/smartcity/rides/acks";
        String topicMessageArrivedInDistrict = "seta/smartcity/rides/notify";

        MqttClient client = null;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();

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

        int qos = 2;
        subscribe(client, topicMessagesAcks, topicMessageArrivedInDistrict);

    }

    private void subscribe(MqttClient client, String topic1, String topic2){
        // Callback
        client.setCallback(new MqttCallback() {

            //TODO: a seconda del messaggio ricevuto fai robe
            public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {
                // Called when a message arrives from the server that matches any subscription made by the client
                if(topic == topic1){
                    //acks

                }else if(topic == topic2){
                    //taxi arrived in a district
                    NotifyFromTaxi notify = NotifyFromTaxi.parseFrom(message.getPayload());
                    District d = notify.getDistrict();
                    alessio_la_greca_990973.smart_city.District true_d;
                    switch(d){
                        case DISTRICT1: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT1;
                        case DISTRICT2: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT2;
                        case DISTRICT3: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT3;
                        case DISTRICT4: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT4;
                    }
                    //TODO: rimanda le pending request del distretto true_d
                }

                /*
                String time = new Timestamp(System.currentTimeMillis()).toString();
                RideRequestMessageOuterClass.RideRequestMessage rrm = RideRequestMessageOuterClass.RideRequestMessage.parseFrom(message.getPayload());
                String receivedMessage = rrm.getId() + " - (" + rrm.getStartingX() + "," +rrm.getStartingY() + ") - (" + + rrm.getArrivingX() + "," +rrm.getArrivingY() + ")";
                System.out.println("Seta Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                        "\n\tTime:    " + time +
                        "\n\tTopic:   " + topic +
                        "\n\tMessage: " + receivedMessage +
                        "\n\tQoS:     " + message.getQos() + "" +
                        "\n\tTime in milliseconds: " + System.currentTimeMillis() + "\n");
                */
            }

            public void connectionLost(Throwable cause) {
                System.out.println("Seta connection lost! cause:" + cause.getMessage() + "-  Thread PID: " + Thread.currentThread().getId());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used here
            }

        });
        int qos = 2;
        System.out.println("seta subscribing to fromtaxi topic...");
        try {
            client.subscribe(topic1, qos);
            client.subscribe(topic2, qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        System.out.println("subscribed!");
    }

    @Override
    public void run() {
        //TODO: serve davvero?
    }
}
