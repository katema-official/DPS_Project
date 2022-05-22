package alessio_la_greca_990973.smart_city.taxi.pollution_simulator;

import alessio_la_greca_990973.simulator.Measurement;
import alessio_la_greca_990973.simulator.PM10Simulator;
import alessio_la_greca_990973.smart_city.taxi.Taxi;

import java.util.ArrayList;
import java.util.List;

public class PollutionSimulatorThread implements Runnable{

    private ArrayList<Measurement> meanMeasurements;
    private int ID;

    private Object alert;
    private MyBuffer buffer;
    private Taxi thisTaxi;

    public PollutionSimulatorThread(Taxi t){
        alert = new Object();
        buffer = new MyBuffer(alert);
        meanMeasurements = new ArrayList<>();
        ID = 0;
        thisTaxi = t;
    }

    @Override
    public void run() {

        PM10Simulator sim = new PM10Simulator(buffer);
        sim.start();

        while(true){        //TODO: !ilTaxiDeveTerminare

            synchronized (alert){
                try {
                    alert.wait();
                    List<Measurement> measures = buffer.readAllAndClean();
                    double value = 0D;
                    for(Measurement m : measures){
                        value += m.getValue();
                    }
                    value = value / 8;
                    Measurement m = new Measurement("pm10_mean-"+(ID++), "PM10", value, System.currentTimeMillis());
                    System.out.println("generated m = " + m.getId() + ", " + m.getValue() + ", " + m.getTimestamp());
                } catch (InterruptedException e) {throw new RuntimeException(e);}
            }


        }





    }

    public void start() {
    }
}
