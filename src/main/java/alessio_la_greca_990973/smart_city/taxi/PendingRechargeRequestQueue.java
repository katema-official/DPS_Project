package alessio_la_greca_990973.smart_city.taxi;

import io.grpc.stub.StreamObserver;
import taxis.service.MiscTaxiServiceOuterClass.*;

import java.util.ArrayList;
import java.util.HashMap;

public class PendingRechargeRequestQueue {

    private ArrayList<StreamObserver<RechargeStationReply>> request;
    private static PendingRechargeRequestQueue instance;

    public PendingRechargeRequestQueue(){
        request = new ArrayList<StreamObserver<RechargeStationReply>>();
    }

    public void sendOkToAllPendingRequests(){
        for(StreamObserver<RechargeStationReply> responseObserver : request) {
            RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }
    }

    public void appendPendingRequest(StreamObserver<RechargeStationReply> pending){
        request.add(pending);
    }


}
