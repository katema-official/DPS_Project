package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.commons.Commons;
import io.grpc.stub.StreamObserver;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.util.ArrayList;

public class PendingRechargeRequestQueue {
    private boolean DEBUG_LOCAL;

    private ArrayList<StreamObserver<RechargeStationReply>> request;
    private Object rechargeQueue_lock;

    public PendingRechargeRequestQueue(){
        rechargeQueue_lock = new Object();
        request = new ArrayList<StreamObserver<RechargeStationReply>>();
    }

    public void sendOkToAllPendingRequests(){
        synchronized (rechargeQueue_lock) {
            for (StreamObserver<RechargeStationReply> responseObserver : request) {
                RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();
                responseObserver.onNext(ok);
                responseObserver.onCompleted();
                debug("[BATTERY] T" + "?" + " -> T" + "?" + " -   ok: i finished");
            }
            request.clear();
        }
    }

    public void appendPendingRequest(StreamObserver<RechargeStationReply> pending){
        synchronized (rechargeQueue_lock){
            request.add(pending);
        }
    }


    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + msg);
        }
    }


}
