package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.smart_city.District;

public class Seta {

    private int progressive_ID = 0;

    public static void main(String args[]){
        System.out.println(District.DISTRICT3.toString().toLowerCase());
    }

    public int generateNewRideRequestID(){
        synchronized (this){
            if(progressive_ID == Integer.MAX_VALUE){
                progressive_ID = 0;
            }
            int ret = progressive_ID;
            progressive_ID++;
            return ret;
        }
    }

}
