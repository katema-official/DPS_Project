package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.commons.Commons;
import alessio_la_greca_990973.smart_city.District;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import taxis.service.MiscTaxiServiceGrpc;
import taxis.service.MiscTaxiServiceGrpc.*;
import taxis.service.MiscTaxiServiceOuterClass;
import taxis.service.MiscTaxiServiceOuterClass.*;

public class BatteryRequest implements Runnable{
    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;
    private TaxiTaxiRepresentation taxiToRequest;
    private double myTimestamp;
    private MiscTaxiServiceOuterClass.District myDistrict;


    public BatteryRequest(Taxi t, BatteryManager bm, TaxiTaxiRepresentation ttr,
                          double myTimestamp, District d){
        thisTaxi = t;
        thisBatteryManager = bm;
        taxiToRequest = ttr;
        this.myTimestamp = myTimestamp;
        switch(d){
            case DISTRICT1: this.myDistrict = MiscTaxiServiceOuterClass.District.DISTRICT1; break;
            case DISTRICT2: this.myDistrict = MiscTaxiServiceOuterClass.District.DISTRICT2; break;
            case DISTRICT3: this.myDistrict = MiscTaxiServiceOuterClass.District.DISTRICT3; break;
            case DISTRICT4: this.myDistrict = MiscTaxiServiceOuterClass.District.DISTRICT4; break;
            case DISTRICT_ERROR: this.myDistrict = MiscTaxiServiceOuterClass.District.DISTRICT_ERROR; break;
        }
    }

    @Override
    public void run() {

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
        MiscTaxiServiceBlockingStub stub = MiscTaxiServiceGrpc.newBlockingStub(channel);

        RechargeStationRequest request =
                RechargeStationRequest.newBuilder()
                        .setId(thisTaxi.getId()).setTimestamp(myTimestamp).setDistrict(this.myDistrict).build();

        RechargeStationReply ok = stub.mayIRecharge(request);
        channel.shutdown();
        return ok;
    }

}
