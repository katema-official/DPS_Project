package alessio_la_greca_990973.smart_city.taxi.rpcservices;


import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryListener;
import io.grpc.stub.StreamObserver;
import taxis.service.MiscTaxiServiceGrpc.*;
import taxis.service.MiscTaxiServiceOuterClass.*;

public class MiscTaxiServiceImpl extends MiscTaxiServiceImplBase {

    private Taxi taxi;
    private BatteryListener batteryListener;
    private boolean DEBUG_LOCAL = true;

    public MiscTaxiServiceImpl(Taxi taxi, BatteryListener bl){
        this.taxi = taxi;
        this.batteryListener = bl;
    }

    @Override
    public void welcomeImANewTaxi(NewTaxiPresentation input, StreamObserver<OldTaxiPresentation> responseObserver){

        //I save the new taxi that presented to me
        TaxiTaxiRepresentation ttr = new TaxiTaxiRepresentation(input.getId(), input.getHostname(), input.getPort(),
                input.getCurrX(), input.getCurrY());
        synchronized (taxi.otherTaxisLock) {
            taxi.getOtherTaxis().put(ttr.getId(), ttr);
        }

        //I tell him where I am.
        OldTaxiPresentation my_presentation = OldTaxiPresentation.newBuilder().setCurrX(taxi.getCurrX()).setCurrY(taxi.getCurrY()).build();

        System.out.println("my_presentation = " + my_presentation);

        responseObserver.onNext(my_presentation);
        responseObserver.onCompleted();
    }



    @Override
    public void mayIRecharge(RechargeStationRequest input, StreamObserver<RechargeStationReply> responseObserver){
        //this gets invoked when another taxi is asking me to recharge

        //if for any reason the taxi asking me to recharge is not in my same district,
        //I reply ok immediately to him.

        RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();

        //this is already synchronized in the getState() method
        int currentState = batteryListener.getThisTaxi().getState();

        if(currentState != Commons.RECHARGING && currentState != Commons.WANT_TO_RECHARGE){
            //if I'm not interested in recharging, I can just send an ok message to the request
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
            debug("Taxi " + batteryListener.getThisTaxi().getId() + " is not interested" +
                    " in recharging. So OK to taxi " + input.getId());
        }else if(currentState == Commons.WANT_TO_RECHARGE){
            //If instead I want to access the resource, check:
            //this guy that is contacting me, asked to access the resource before me?
            if(input.getTimestamp() < taxi.getBatteryManager().getTimestampOfRequest()){
                responseObserver.onNext(ok);
                responseObserver.onCompleted();
                debug("Taxi " + batteryListener.getThisTaxi().getId() + " is interested" +
                        " in recharging, but taxi " + input.getId() +" arrived before. So, OK!");
            }else{
                //Otherwise, I asked to access before him, so I must put him in the queue.
                batteryListener.getQueue().appendPendingRequest(responseObserver);
                debug("Taxi " + batteryListener.getThisTaxi().getId() + " is interested" +
                        " in recharging, and arrived before taxi " + input.getId() +". So, WAIT!");
            }
        }else if(currentState == Commons.RECHARGING){
            //if I'm using the resource, I can't reply immediately. Others will have to wait
            batteryListener.getQueue().appendPendingRequest(responseObserver);
            debug("Taxi " + batteryListener.getThisTaxi().getId() + " is using the recharge" +
                    " station. So wait, taxi " + input.getId() + "!");
        }
    }

    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + msg);
        }
    }

}
