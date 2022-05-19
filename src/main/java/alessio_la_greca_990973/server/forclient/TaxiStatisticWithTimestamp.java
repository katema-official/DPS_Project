package alessio_la_greca_990973.server.forclient;

import java.util.List;

public class TaxiStatisticWithTimestamp extends TaxiStatistic{

    private double timestamp;
    private List<Double> pollutionAverages;

    public TaxiStatisticWithTimestamp(int kilometers, int rides, List<Double> pollutionAverages, int batteryLevel, double timestamp){
        super(kilometers, rides, 0D, batteryLevel);
        this.timestamp = timestamp;
        this.pollutionAverages = pollutionAverages;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public List<Double> getPollutionAverages(){return pollutionAverages;}

    public void setPollutionAverages(List<Double> l){this.pollutionAverages = l;}
}
