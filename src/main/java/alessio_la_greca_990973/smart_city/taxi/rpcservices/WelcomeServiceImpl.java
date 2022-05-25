package alessio_la_greca_990973.smart_city.taxi.rpcservices;

import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;
import io.grpc.stub.StreamObserver;
import taxis.welcome.WelcomeServiceGrpc;
import taxis.welcome.WelcomeTaxiService.*;
import taxis.welcome.WelcomeServiceGrpc.*;

public class WelcomeServiceImpl extends WelcomeServiceImplBase {

    private Taxi taxi;

    public WelcomeServiceImpl(Taxi taxi){
        this.taxi = taxi;
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





}
