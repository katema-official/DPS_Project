package alessio_la_greca_990973.smart_city.taxi.rpcservices;

import alessio_la_greca_990973.smart_city.taxi.PendingRechargeRequestQueue;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryListener;
import io.grpc.stub.StreamObserver;
import taxis.recharge.MutualExclusionBatteryStationService.*;
import taxis.recharge.RechargeRequestServiceGrpc.RechargeRequestServiceImplBase;

public class RechargeRequestServiceImpl extends RechargeRequestServiceImplBase {

    private BatteryListener batteryListener;

    public RechargeRequestServiceImpl(BatteryListener bl){
        this.batteryListener = bl;
    }

    @Override
    public void mayIRecharge(RechargeStationRequest input, StreamObserver<RechargeStationReply> responseObserver){
        //this gets invoked when another taxi is asking me to recharge

        //if for any reason the taxi asking me to recharge is not in my same district,
        //I reply ok immediately to him.

        RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();

        //this is already synchronized in the getRechargeState()
        int currentState = batteryListener.getThisBatteryManager().getRechargeState();

        if(currentState == 0){
            //if I'm not interested in recharging, I can just send an ok message to the request
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }else if(currentState == 1){
            //If instead I want to access the resource, check:
            //this guy that is contacting me, asked to access the resource before me?
            if(input.getTimestamp() < batteryListener.getThisBatteryManager().getTimestampOfRequest()){
                responseObserver.onNext(ok);
                responseObserver.onCompleted();
            }else{
                //Otherwise, I asked to access before him, so I must put him in the queue.
                PendingRechargeRequestQueue.getInstance().appendPendingRequest(responseObserver);
            }
        }else{
            //if I'm using the resource, I can't reply immediately. Others will have to wait
            PendingRechargeRequestQueue.getInstance().appendPendingRequest(responseObserver);
        }
    }

}
