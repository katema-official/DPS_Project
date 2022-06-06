package alessio_la_greca_990973.smart_city.taxi;

import io.grpc.stub.StreamObserver;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.util.ArrayList;

public class PendingRechargeRequestQueue {

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
            }
            request.clear();
        }
    }

    public void appendPendingRequest(StreamObserver<RechargeStationReply> pending){
        synchronized (rechargeQueue_lock){
            request.add(pending);
        }
    }


}
