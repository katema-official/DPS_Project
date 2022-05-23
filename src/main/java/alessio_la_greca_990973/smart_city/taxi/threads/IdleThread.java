package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.*;
import ride.request.RideRequestMessageOuterClass;

import java.sql.Timestamp;
import java.util.Scanner;

public class IdleThread implements Runnable{

    private Taxi thisTaxi;
    private MqttClient client;
    private String broker;
    private String clientId;
    private int qos;

    public IdleThread(Taxi t){
        thisTaxi = t;

        //let's start the MQTT client
        broker = "tcp://localhost:1883";
        clientId = MqttClient.generateClientId();
        qos = 2;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            System.out.println(clientId + " Connecting Broker " + broker);
            client.connect(connOpts);
            System.out.println(clientId + " Connected");


            // Callback
            client.setCallback(new MqttCallback() {

                public void messageArrived(String topic, MqttMessage message) throws InvalidProtocolBufferException {
                    // Called when a message arrives from the server that matches any subscription made by the client
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    RideRequestMessageOuterClass.RideRequestMessage rrm = RideRequestMessageOuterClass.RideRequestMessage.parseFrom(message.getPayload());
                    String receivedMessage = rrm.getId() + " - (" + rrm.getStartingX() + "," + rrm.getStartingY() + ") - (" + +rrm.getArrivingX() + "," + rrm.getArrivingY() + ")";
                    System.out.println(clientId + " Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "" +
                            "\n\tTime in milliseconds: " + System.currentTimeMillis() + "\n");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connectionlost! cause:" + cause.getMessage() + "-  Thread PID: " + Thread.currentThread().getId());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used here
                }

            });

        } catch (MqttException me) {
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
        //first thing first: the taxi subscribes to the district to which it belongs right now
        District d = SmartCity.getDistrict(thisTaxi.getCurrX(), thisTaxi.getCurrY());
        subscribeToADistrictTopic(d);
    }





    private void subscribeToADistrictTopic(District d){



        String topic = "seta/smartcity/rides/" + d.toString().toLowerCase();
        try{

            //if the client was already subscribed to a district, it must now unsubscribe from them
            client.unsubscribe("seta/smartcity/rides/+");   //TODO: lancia errore se non è registrato? devo mettere un array?

            System.out.println(thisTaxi.getId() + " subscribing to district " + d.toString().toLowerCase());
            client.subscribe(topic, qos);
            System.out.println(thisTaxi.getId() + " subscribed to district " + d.toString().toLowerCase());

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
    }

    private void closeMqttSubscriberConnection(){
        try {
            client.disconnect();
        }catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
    }




}
