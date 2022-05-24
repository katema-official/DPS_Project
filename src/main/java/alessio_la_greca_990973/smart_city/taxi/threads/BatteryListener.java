package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.rpcservices.RechargeRequestServiceImpl;
import alessio_la_greca_990973.smart_city.taxi.rpcservices.WelcomeServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class BatteryListener implements Runnable{

    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;
    private Server rechargeListenerService;

    public BatteryListener(Taxi t, BatteryManager bm){
        thisTaxi = t;
        thisBatteryManager = bm;

    }

    @Override
    public void run() {
        //se mi arriva la richesta da qualcuno che non è nel mio distretto (raro ma può succedere), gli rispondo
        //ok
        rechargeListenerService = ServerBuilder.forPort(thisTaxi.getPort()).addService(
                new RechargeRequestServiceImpl(this)).build();
        try {
            rechargeListenerService.start();
        } catch (IOException e) {e.printStackTrace();}

    }

    public Taxi getThisTaxi() {
        return thisTaxi;
    }

    public void setThisTaxi(Taxi thisTaxi) {
        this.thisTaxi = thisTaxi;
    }

    public BatteryManager getThisBatteryManager() {
        return thisBatteryManager;
    }

    public void setThisBatteryManager(BatteryManager thisBatteryManager) {
        this.thisBatteryManager = thisBatteryManager;
    }
}
