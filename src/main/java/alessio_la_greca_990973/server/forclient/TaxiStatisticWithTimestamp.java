package alessio_la_greca_990973.server.forclient;

import java.util.List;

public class TaxiStatisticWithTimestamp extends TaxiStatistic{

    private long timestamp;

    public TaxiStatisticWithTimestamp(int kilometers, int rides, List<Double> pollutionAverages, int batteryLevel, long timestamp){
        super(kilometers, rides, pollutionAverages, batteryLevel);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
