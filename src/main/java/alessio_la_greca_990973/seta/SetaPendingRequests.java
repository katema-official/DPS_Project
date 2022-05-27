package alessio_la_greca_990973.seta;

import ride.request.RideRequestMessageOuterClass.*;

import java.util.ArrayList;
import java.util.HashMap;

public class SetaPendingRequests {
    private HashMap<Integer, ArrayList<RideRequestMessage>> pendingRequestsHashMap;

    public SetaPendingRequests(){
        pendingRequestsHashMap = new HashMap<>();
        pendingRequestsHashMap.put(1, new ArrayList<>());
        pendingRequestsHashMap.put(2, new ArrayList<>());
        pendingRequestsHashMap.put(3, new ArrayList<>());
        pendingRequestsHashMap.put(4, new ArrayList<>());

    }

}
