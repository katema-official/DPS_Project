package alessio_la_greca_990973.smart_city.taxi.pollution_simulator;

import alessio_la_greca_990973.simulator.Buffer;
import alessio_la_greca_990973.simulator.Measurement;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class MyBuffer implements Buffer {

    private Measurement[] measures = new Measurement[8];
    private int i;
    private int windowStart;
    private int windowSize;
    private Object alert;

    public MyBuffer(Object alert){
        this.alert = alert;
        i = 0;
        windowSize = 0;
        windowStart = 0;
    }
    @Override
    public void addMeasurement(Measurement m) {
        measures[i] = m;
        i++;
        windowSize++;

        if(i == 8) i = 0;

        if(windowSize == 8){
            synchronized (alert) {
                alert.notify();
            }
        }
    }

    @Override
    public List<Measurement> readAllAndClean() {
        ArrayList<Measurement> res = new ArrayList<>();

        for(int j = 0; j <8; j++){
            res.add(measures[(j+windowStart)%8]);
        }

        windowSize = 4;
        windowStart = (windowStart + 4) % 8;

        return res;
    }
}
