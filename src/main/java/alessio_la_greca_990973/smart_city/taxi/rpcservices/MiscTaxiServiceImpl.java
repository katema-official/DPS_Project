package alessio_la_greca_990973.smart_city.taxi.rpcservices;


import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.SmartCity;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import alessio_la_greca_990973.smart_city.taxi.threads.BatteryListener;
import alessio_la_greca_990973.smart_city.taxi.threads.IdleThread;
import io.grpc.stub.StreamObserver;
import taxis.service.MiscTaxiServiceGrpc.*;
import taxis.service.MiscTaxiServiceOuterClass;
import taxis.service.MiscTaxiServiceOuterClass.*;

public class MiscTaxiServiceImpl extends MiscTaxiServiceImplBase {

    private Taxi taxi;
    private BatteryListener batteryListener;
    private IdleThread idleThread;

    public MiscTaxiServiceImpl(Taxi taxi, BatteryListener bl, IdleThread it){
        this.taxi = taxi;
        this.batteryListener = bl;
        this.idleThread = it;
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

        responseObserver.onNext(my_presentation);
        responseObserver.onCompleted();
    }



    @Override
    public void mayIRecharge(RechargeStationRequest input, StreamObserver<RechargeStationReply> responseObserver){
        //this gets invoked when another taxi is asking me to recharge

        //if I'm exiting, i reply ok to everyone
        RechargeStationReply ok = RechargeStationReply.newBuilder().setOk(true).build();
        if(taxi.getState() == Commons.EXITING){
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }

        //if taxi asking me to recharge is not in my same district,
        //I reply ok immediately to him.

        if(!input.getDistrict().toString().equals(SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()).toString())){
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }else{

            //this is already synchronized in the getState() method
            int currentState = batteryListener.getThisTaxi().getState();

            if(currentState != Commons.RECHARGING && currentState != Commons.WANT_TO_RECHARGE){
                //if I'm not interested in recharging, I can just send an ok message to the request
                responseObserver.onNext(ok);
                responseObserver.onCompleted();
            }else if(currentState == Commons.WANT_TO_RECHARGE){
                //If instead I want to access the resource, check:
                //this guy that is contacting me, asked to access the resource before me?
                if(input.getTimestamp() < taxi.getBatteryManager().getTimestampOfRequest()){
                    responseObserver.onNext(ok);
                    responseObserver.onCompleted();
                }else{
                    //Otherwise, I asked to access before him, so I must put him in the queue.
                    batteryListener.getQueue().appendPendingRequest(responseObserver);
                }
            }else if(currentState == Commons.RECHARGING){
                //if I'm using the resource, I can't reply immediately. Others will have to wait
                batteryListener.getQueue().appendPendingRequest(responseObserver);
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

        //if I'm exiting, I can reply ok immediately
        if(taxi.getState() == Commons.EXITING){
            responseObserver.onNext(yes);
            responseObserver.onCompleted();
            return;
        }

        //if the request comes from a taxi of another district, I can reply ok to him immediately
        if(SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()) != SmartCity.getDistrict(input.getX(), input.getY())){
            responseObserver.onNext(yes);
            responseObserver.onCompleted();
            return;
        }

        //if I'm already involved in a ride (or i'm involved in a recharge process), I can reply ok immediately
        if(taxi.getState() == Commons.RIDING || taxi.getState() == Commons.WANT_TO_RECHARGE || taxi.getState()==Commons.RECHARGING){
            responseObserver.onNext(yes);
            responseObserver.onCompleted();
            return;
        }



        //if instead the request is of my same district AND I'm not riding (or in a state in which I respond yes), then...
        //if this other taxi is asking me about another request, I reply to him immediately, so that we don't let
        //the customers wait
        if(input.getIdRideRequest() != idleThread.currentRequestBeingProcessed){
            //if I know it has already been satisfied, I can respond no to him
            if(idleThread.getIncomingRequestValue(input.getIdRideRequest()) == true){
                responseObserver.onNext(no);
                responseObserver.onCompleted();
            }else {
                //else I respond yes
                responseObserver.onNext(yes);
                responseObserver.onCompleted();
            }
        }else{
            //in this case, the request is from a taxi from my same district AND the id of the taxi request is the same
            //as the one I'm currently considering.
            //The last question is: is this taxi in my set of participating taxis for this election?
            if(!idleThread.isThisTaxiInThisElection(input.getTaxiId())){
                //if no, let's just tell him that another taxi between all of us will take care of this ride
                responseObserver.onNext(no);
                responseObserver.onCompleted();
            }else{
                //if yes, let's see: who's closer? Who has the most battery? Who has the highest ID?
                boolean res = idleThread.compareTaxis(input);
                if(res){
                    responseObserver.onNext(yes);
                    responseObserver.onCompleted();
                }else{
                    responseObserver.onNext(no);
                    responseObserver.onCompleted();
                }
            }
        }

    }

    @Override
    public void iTookCareOfThisRequest(SatisfiedRequest sr, StreamObserver<MiscTaxiServiceOuterClass.Void> responseObserver){

        District d = District.DISTRICT_ERROR;
        switch(sr.getDistrict()){
            case DISTRICT1: d = District.DISTRICT1; break;
            case DISTRICT2: d = District.DISTRICT2; break;
            case DISTRICT3: d = District.DISTRICT3; break;
            case DISTRICT4: d = District.DISTRICT4; break;
            case DISTRICT_ERROR: d = District.DISTRICT_ERROR; break;
        }

        synchronized (taxi.stateLock) {
            if(SmartCity.getDistrict(taxi.getCurrX(), taxi.getCurrY()) == d){
                idleThread.setIncomingRequestToTrue(sr.getReqId());
            }
        }

        responseObserver.onNext(MiscTaxiServiceOuterClass.Void.newBuilder().build());
        responseObserver.onCompleted();

    }


    @Override
    public void iAmExiting(ExitingAnnouncement ea, StreamObserver<ExitingOk> streamObserver) {
        //used to notify a taxi that another one (the one contained in ea) is exiting from the smart city
        int idToRemove = ea.getTaxiId();
        synchronized (taxi.otherTaxisLock) {
            taxi.getOtherTaxis().remove(idToRemove);
        }
        ExitingOk response = ExitingOk.newBuilder().setOk(true).build();
        streamObserver.onNext(response);
        streamObserver.onCompleted();
    }


}
