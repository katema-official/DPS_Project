package alessio_la_greca_990973.seta;

import alessio_la_greca_990973.smart_city.District;
import ride.request.RideRequestMessageOuterClass.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

public class Seta {

    private static int progressive_ID = 0;
    private static Object progressive_id_lock;
    private static Object pending_requests_lock;
    private static HashMap<District, ArrayList<RideRequestMessage>> pendingRequestsHashMap;

    public static void main(String args[]){

        progressive_id_lock = new Object();
        pending_requests_lock = new Object();

        pendingRequestsHashMap = new HashMap<>();
        pendingRequestsHashMap.put(District.DISTRICT1, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT2, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT3, new ArrayList<>());
        pendingRequestsHashMap.put(District.DISTRICT4, new ArrayList<>());

        SetaSubscriberThread sst = new SetaSubscriberThread();
        Thread t0 = new Thread(sst);
        t0.start();

        RideRequestThread r1 = new RideRequestThread();
        RideRequestThread r2 = new RideRequestThread();
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);
        t1.start();
        t2.start();

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
        synchronized (pending_requests_lock){
            return pendingRequestsHashMap.get(district);
        }
    }

    public static void removePendingRequest(int id, District district){
        synchronized (pending_requests_lock){
            ArrayList<RideRequestMessage> requests = pendingRequestsHashMap.get(district);
            RideRequestMessage toRemove = null;
            for(RideRequestMessage req : requests){
                if(req.getId() == id){
                    toRemove = req;
                }
            }
            if(toRemove != null){
                requests.remove(id);
                pendingRequestsHashMap.put(district, requests);
            }
        }
    }



}
