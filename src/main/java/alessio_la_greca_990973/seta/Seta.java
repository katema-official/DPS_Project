package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import ride.request.RideRequestMessageOuterClass.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

public class Seta {

    private static int progressive_ID = 0;
    public static Object progressive_id_lock;
    public static Object pending_requests_lock;
    private static HashMap<District, ArrayList<RideRequestMessage>> pendingRequestsHashMap;

    public static void main(String args[]){

        progressive_id_lock = new Object();
        pending_requests_lock = new Object();

        pendingRequestsHashMap = new HashMap<>();
        pendingRequestsHashMap.put(District.DISTRICT1, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT2, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT3, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT4, new ArrayList<>());

        RideRequestThread r1 = new RideRequestThread();
        //SetaSubscriberThread sst = new SetaSubscriberThread(r1);
        //Thread t0 = new Thread(sst);
        //t0.start();


        Thread t1 = new Thread(r1);
        t1.start();

    }

    public static int generateNewRideRequestID(){
        synchronized (progressive_id_lock){
            if(progressive_ID == Integer.MAX_VALUE){
                progressive_ID = 0;
            }
            int ret = progressive_ID;
            progressive_ID++;
            return ret;
        }
    }

    public static void addPendingRequest(District district, RideRequestMessage request){
        synchronized (pending_requests_lock) {
            pendingRequestsHashMap.get(district).add(request);
        }
    }

    public static ArrayList<RideRequestMessage> getPendingRequests(District district){
        ArrayList<RideRequestMessage> ret = null;
        synchronized (pending_requests_lock){
            ret = new ArrayList(pendingRequestsHashMap.get(district));
            return ret;
        }
    }

    public static void removePendingRequest(int id, District district){
        if(district == District.DISTRICT_ERROR){
            System.out.println("SETA: error in removing request of id " + id + ". The district is unknown!");
        }
        synchronized (pending_requests_lock){
            ArrayList<RideRequestMessage> requests = pendingRequestsHashMap.get(district);
            RideRequestMessage toRemove = null;
            for(RideRequestMessage req : requests){
                if(req.getId() == id){
                    toRemove = req;
                }
            }
            if(toRemove != null){
                requests.remove(toRemove);
                pendingRequestsHashMap.put(district, requests);
            }

        }
    }






}
