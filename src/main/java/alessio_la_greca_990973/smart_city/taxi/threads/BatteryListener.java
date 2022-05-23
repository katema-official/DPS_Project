package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.taxi.Taxi;

public class BatteryListener implements Runnable{

    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;

    public BatteryListener(Taxi t, BatteryManager bm){
        thisTaxi = t;
        thisBatteryManager = bm;

    }

    @Override
    public void run() {
        //se mi arriva la richesta da qualcuno che non è nel mio distretto (raro ma può succedere), gli rispondo
        //ok
    }
}
