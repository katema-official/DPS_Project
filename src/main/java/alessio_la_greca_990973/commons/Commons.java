package alessio_la_greca_990973.commons;

public class Commons {

    //states of a taxi
    public static int INITIALIZING = -1;
    public static int IDLE = 0;
    public static int WANT_TO_RECHARGE = 1;
    public static int RECHARGING = 2;
    public static int ELECTING = 3;
    public static int RIDING = 4;
    public static int EXITING = 5;



    public static String topicMessagesAcks = "seta/smartcity/taxi/acks";
    public static String topicMessageArrivedInDistrict = "seta/smartcity/taxi/notify";
}
