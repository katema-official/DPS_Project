package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.TaxiTaxiRepresentation;

public class BatteryRequest implements Runnable{
    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;
    private TaxiTaxiRepresentation taxiToRequest;

    public BatteryRequest(Taxi t, BatteryManager bm, TaxiTaxiRepresentation ttr){
        thisTaxi = t;
        thisBatteryManager = bm;
    }

    @Override
    public void run() {


        //fanno una richiesta sincrona, e, quando ricevono l'ok...
        thisBatteryManager.addAck();    //this is already synchronized

    }
}
