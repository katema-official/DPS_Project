package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import taxis.recharge.MutualExclusionBatteryStationService;
import taxis.recharge.MutualExclusionBatteryStationService.*;
import taxis.recharge.RechargeRequestServiceGrpc;
import taxis.recharge.RechargeRequestServiceGrpc.*;

public class BatteryRequest implements Runnable{
    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;
    private TaxiTaxiRepresentation taxiToRequest;
    private double myTimestamp;
    private RechargeStationRequest.District myDistrict;

    private boolean DEBUG_LOCAL;

    public BatteryRequest(Taxi t, BatteryManager bm, TaxiTaxiRepresentation ttr,
                          double myTimestamp, District myDistrict){
        thisTaxi = t;
        thisBatteryManager = bm;
        taxiToRequest = ttr;
        this.myTimestamp = myTimestamp;
        switch(myDistrict){
            case DISTRICT1: this.myDistrict = RechargeStationRequest.District.DISTRICT1;
            case DISTRICT2: this.myDistrict = RechargeStationRequest.District.DISTRICT2;
            case DISTRICT3: this.myDistrict = RechargeStationRequest.District.DISTRICT3;
            case DISTRICT4: this.myDistrict = RechargeStationRequest.District.DISTRICT4;
            case DISTRICT_ERROR: this.myDistrict = RechargeStationRequest.District.DISTRICT_ERROR;
        }
    }

    @Override
    public void run() {
        debug("I, taxi " + thisTaxi.getId() + ", am asking to taxi " + taxiToRequest.getId() +
                " to accesso to the recharge station of district " + this.myDistrict);

        //I ask to another taxi of my district (or, at least, that was present in my district at the moment
        //of the request) if I can get access to the shared resource, that is, the recharge station.
        RechargeStationReply ok = synchronousCallRechargeRequest();

        //since this call is synchronous, this code will be executed only when the other taxi has replied
        //to me with an ok message
        if(ok.getOk() == true) {    //not necessary, but for eventual future changes...
            thisBatteryManager.addAck();    //this is already synchronized
        }
    }

    private RechargeStationReply synchronousCallRechargeRequest(){
        String host = taxiToRequest.getHostname();
        int port = taxiToRequest.getListeningPort();
        ManagedChannel channel = ManagedChannelBuilder.forTarget(host + ":" + port).usePlaintext().build();
        RechargeRequestServiceBlockingStub stub = RechargeRequestServiceGrpc.newBlockingStub(channel);

        RechargeStationRequest request =
                MutualExclusionBatteryStationService.RechargeStationRequest.newBuilder()
                        .setId(thisTaxi.getId()).setTimestamp(myTimestamp).setDistrict(this.myDistrict).build();

        RechargeStationReply ok = stub.mayIRecharge(request);
        channel.shutdown();
        return ok;
    }

    private void debug(String msg){
        if(Commons.DEBUG_GLOBAL && DEBUG_LOCAL){
            System.out.println("debug: " + msg);
        }
    }

}
