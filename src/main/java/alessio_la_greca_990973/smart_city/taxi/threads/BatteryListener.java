package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.taxi.PendingRechargeRequestQueue;
import alessio_la_greca_990973.smart_city.taxi.Taxi;
import alessio_la_greca_990973.smart_city.taxi.rpcservices.RechargeRequestServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class BatteryListener implements Runnable{

    private Taxi thisTaxi;
    private BatteryManager thisBatteryManager;
    private Server rechargeListenerService;
    private PendingRechargeRequestQueue queue;

    public BatteryListener(Taxi t, BatteryManager bm, PendingRechargeRequestQueue queue){
        thisTaxi = t;
        thisBatteryManager = bm;
        this.queue = queue;
    }

    @Override
    public void run() {
        //TODO: da rivedere
        int port = thisTaxi.getPort() + 500;
        rechargeListenerService = ServerBuilder.forPort(port).addService(
                new RechargeRequestServiceImpl(this)).build();
        try {
            rechargeListenerService.start();
        } catch (IOException e) {e.printStackTrace();}

    }

    public Taxi getThisTaxi() {
        return thisTaxi;
    }

    public BatteryManager getThisBatteryManager() {
        return thisBatteryManager;
    }

    public PendingRechargeRequestQueue getQueue() {
        return queue;
    }


    public void shutdownListenerServer(){
        rechargeListenerService.shutdownNow();
    }
}
