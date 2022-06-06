package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass.*;

import java.sql.Timestamp;

public class SetaSubscriberThread implements Runnable{

    private boolean DEBUG_LOCAL = true;
    private int qos;

    private MqttClient client;
    private RideRequestThread rideRequestThread;

    public SetaSubscriberThread(RideRequestThread rideRequestThread){
        //this are the topics on which seta subscribes. Here, it will receive
        //from taxis messages that say either they are now in a particular
        //district or the acks about a particular ride

        this.rideRequestThread = rideRequestThread;
        client = null;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();   //MqttClient

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

        subscribe();

    }

    private void subscribe(){
        // Callback
        client.setCallback(new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {

                if(topic.equals(Commons.topicMessagesAcks)){
                    //acks, that means, this particular ride has been accomplished
                    AckFromTaxi ack = AckFromTaxi.parseFrom(message.getPayload());
                    debug("acked request " + ack.getIdRequest() + " of district " + ack.getDistrict());
                    alessio_la_greca_990973.smart_city.District d = alessio_la_greca_990973.smart_city.District.DISTRICT_ERROR;
                    switch(ack.getDistrict()){
                        case DISTRICT1: d = alessio_la_greca_990973.smart_city.District.DISTRICT1; break;
                        case DISTRICT2: d = alessio_la_greca_990973.smart_city.District.DISTRICT2; break;
                        case DISTRICT3: d = alessio_la_greca_990973.smart_city.District.DISTRICT3; break;
                        case DISTRICT4: d = alessio_la_greca_990973.smart_city.District.DISTRICT4; break;
                        case DISTRICT_ERROR: d = alessio_la_greca_990973.smart_city.District.DISTRICT_ERROR; break;
                    }
                    Seta.removePendingRequest(ack.getIdRequest(), d);
                }else if(topic.equals(Commons.topicMessageArrivedInDistrict)){
                    //taxi arrived in a district
                    NotifyFromTaxi notify = NotifyFromTaxi.parseFrom(message.getPayload());
                    alessio_la_greca_990973.smart_city.District true_d = alessio_la_greca_990973.smart_city.District.DISTRICT_ERROR;
                    switch(notify.getDistrict()){
                        case DISTRICT1: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT1; break;
                        case DISTRICT2: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT2; break;
                        case DISTRICT3: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT3; break;
                        case DISTRICT4: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT4; break;
                        case DISTRICT_ERROR: true_d = alessio_la_greca_990973.smart_city.District.DISTRICT_ERROR; break;
                    }
                    String last = true_d.toString().toLowerCase();
                    String resend_topic = "seta/smartcity/rides/" + last;
                    //now that we have the topic, let's send again the pending requests for that district.
                    debug("sending once again the pending requests for district " + true_d + ", since a taxi notified its presence there. " +
                            "The number of pending requests is " + Seta.getPendingRequests(true_d).size());
                    for(RideRequestMessage rrm : Seta.getPendingRequests(true_d)) {
                        MqttMessage resend_message = new MqttMessage(rrm.toByteArray());
                        // Set the QoS on the Message
                        message.setQos(qos);

                        rideRequestThread.send(resend_topic, resend_message, rrm.getId());

                        debug("in particular, sending request ID " + rrm.getId());
                    }
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
                System.out.println("Seta connection lost! cause:" + cause.getMessage() + ",\n " + cause.getCause() + ", \n" +
                        cause.getStackTrace() + " -  Thread PID: " + Thread.currentThread().getId());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used here
            }

        });
        int qos = 2;
        System.out.println("seta subscribing to ack and notify topics...");
        try {
            client.subscribe(Commons.topicMessagesAcks, qos);
            client.subscribe(Commons.topicMessageArrivedInDistrict, qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        System.out.println("subscribed!");
    }



    @Override
    public void run() {
        //TODO: serve davvero?
        while(true){

        }
    }

    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug SETA: " + msg);
        }
    }
}
