package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass;
import ride.request.RideRequestMessageOuterClass.RideRequestMessage;

import java.util.Random;

public class RideRequestThread implements Runnable{

    private int requestDelay = 5000;    //so it can be changed if needed
    private MqttClient client;
    private int qos;

    private Object requests_lock;

    public RideRequestThread(){
        requests_lock = new Object();
        //the request thread must register as publishers on the MQTT broker
        client = null;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        qos = 2;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setMaxInflight(1000);
            // Connect the client
            System.out.println(clientId + " RideRequestThread connecting to broker " + broker);
            client.connect(connOpts);
            System.out.println(clientId + " Connected!");

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

    @Override
    public void run() {

        while(true) {
            //this thread must generate, each five seconds, in two random moments, two ride request.
            try {
                Random rand = new Random();

                int millis1 = rand.nextInt(requestDelay);
                Thread.sleep(millis1);
                generateRequest();

                int millis2  = rand.nextInt(requestDelay - millis1);

                Thread.sleep(millis2);
                generateRequest();

                int residual = requestDelay - millis1 - millis2;
                if(residual > 0) {
                    Thread.sleep(residual);
                }

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void generateRequest(){
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
        send(topic, message, ID);


    }




    private void subscribe(){
        // Callback
        client.setCallback(new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {

                if(topic.equals(Commons.topicMessagesAcks)){
                    //acks, that means, this particular ride has been accomplished
                    RideRequestMessageOuterClass.AckFromTaxi ack = RideRequestMessageOuterClass.AckFromTaxi.parseFrom(message.getPayload());
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
                    RideRequestMessageOuterClass.NotifyFromTaxi notify = RideRequestMessageOuterClass.NotifyFromTaxi.parseFrom(message.getPayload());
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

                    resend(resend_topic, true_d);
                }

            }

            public void connectionLost(Throwable cause) {
                System.out.println("Seta connection lost! cause:" + cause.getMessage() + ",\n " + cause.getCause() + ", \n" +
                        cause.getStackTrace() + ", \n" + cause.getLocalizedMessage() + " -  Thread PID: " + Thread.currentThread().getId());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {

            }

        });
        System.out.println("seta subscribing to ack and notify topics...");
        try {
            client.subscribe(Commons.topicMessagesAcks, qos);
            client.subscribe(Commons.topicMessageArrivedInDistrict, qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        System.out.println("subscribed!");
    }





    public void send(String topic, MqttMessage message, int IDrequest){
        synchronized (requests_lock){
            try {
                client.publish(topic, message);
            } catch (MqttException e) {
                System.out.println("SETA: errore! " + e.getMessage() + ", \n" + e.getReasonCode() + ", \n" + e.getCause() +
                        ", \n" + e.getStackTrace() + ", \n" + e.toString());
                throw new RuntimeException(e);
            }
        }
    }


    public void resend(String resend_topic, alessio_la_greca_990973.smart_city.District true_d){
        //now that we have the topic, let's send again the pending requests for that district.
        for(RideRequestMessage rrm : Seta.getPendingRequests(true_d)) {
            MqttMessage resend_message = new MqttMessage(rrm.toByteArray());
            // Set the QoS on the Message
            resend_message.setQos(qos);

            send(resend_topic, resend_message, rrm.getId());
        }

    }

}
