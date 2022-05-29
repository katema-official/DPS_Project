package alessio_la_greca_990973.smart_city.taxi.rpcservices;


import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.SmartCity;
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

        //if taxi asking me to recharge is not in my same district,
        //I reply ok immediately to him.
        RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();
        debug("his district = " + input.getDistrict().toString());
        debug("my district = " + SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()));
        if(!input.getDistrict().toString().equals(
                SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()).toString())){
            debug("Taxi " + taxi.getId() + " is of different district in respect" +
                    " with taxi " + input.getId() +", so ok, recharge, my friend!");
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }else{

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
    }

    @Override
    public void mayITakeCareOfThisRequest(TaxiCoordinationRequest input, StreamObserver<TaxiCoordinationReply> responseObserver){
        //this method is invoked when another taxi in the city asks me to take care of a ride.
        //I will either respond to him with an ok message ("yes, you can take care of this") or a not ok message
        //("no, I will handle this request if no one has better characteristics than me")

        TaxiCoordinationReply yes = TaxiCoordinationReply.newBuilder().setOk(true).build();
        TaxiCoordinationReply no = TaxiCoordinationReply.newBuilder().setOk(false).build();
        //if the request comes from a taxi of another district, I can reply ok to him immediately
        if(SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()) != SmartCity.getDistrict(input.getX(), input.getY())){
            responseObserver.onNext(yes);
            responseObserver.onCompleted();
        }

        //if I already satisfied this request in the past, I can reply not ok to him immediately
        if(taxi.satisfiedRides.contains(input.getTaxiId())){
            responseObserver.onNext(no);
            responseObserver.onCompleted();
        }

        //if instead the request is of my same district AND I haven't satisfied it already, then...
        //we are assuming that taxis of the same district receive the requests for their district in the same order.
        //For instance, if the ride requests with ID 10 and 11 are published, and they both are relative to district "i",
        //all taxis of district "i" will receive before request 10 and then request 11. So, if a taxi receives a request
        //relative to a ride with ID greater than the one it's currently processing, it must put that request on a
        //waiting list (read as: "sorry, I can't reply to you right now because I still haven't reached taht request yet,
        //but when I'll have, I'll respond to you asap").
        //With the same logic, a taxi shouldn't receive a request from a taxi of the same district with ID (of the request)
        //lower than the one it is currently processing. But, if it does, let's just answer him "...yeah, you can take
        //care of that request...", but we know that some other taxi will have replied to him with the no message saying
        //"sorry bro, I already handled that"
        if(){//la richiesta che mi arriva ha un id minore di quello della richiesta che sto gestendo in questo momento
            responseObserver.onNext(yes);
            responseObserver.onCompleted();
        }

    }

    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + msg);
        }
    }

}
