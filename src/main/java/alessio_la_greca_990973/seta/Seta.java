package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.smart_city.District;

public class Seta {

    private static int progressive_ID = 0;

    public static void main(String args[]){

        RideRequestThread r1 = new RideRequestThread();
        RideRequestThread r2 = new RideRequestThread();
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);
        t1.start();
        t2.start();

    }

    public static int generateNewRideRequestID(){
        synchronized (Seta.class){
            if(progressive_ID == Integer.MAX_VALUE){
                progressive_ID = 0;
            }
            int ret = progressive_ID;
            progressive_ID++;
            return ret;
        }
    }

}
