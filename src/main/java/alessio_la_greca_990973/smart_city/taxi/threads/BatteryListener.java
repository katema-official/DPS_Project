package alessio_la_greca_990973.smart_city.taxi.threads;

import alessio_la_greca_990973.smart_city.taxi.PendingRechargeRequestQueue;
import alessio_la_greca_990973.smart_city.taxi.Taxi;

public class BatteryListener{

    private Taxi thisTaxi;
    private PendingRechargeRequestQueue queue;

    public BatteryListener(Taxi t, PendingRechargeRequestQueue queue){
        thisTaxi = t;
        this.queue = queue;
    }

    public Taxi getThisTaxi() {
        return thisTaxi;
    }

    public PendingRechargeRequestQueue getQueue() {
        return queue;
    }

}
