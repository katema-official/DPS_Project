package alessio_la_greca_990973.smart_city.taxi;

import org.eclipse.paho.client.mqttv3.MqttClient;

public class SubscriberExample {
    public static void main(String[] args) {
        MqttClient client;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        String topic = "home/controllers/temp";
        int qos = 2;
    }
}
