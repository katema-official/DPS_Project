package alessio_la_greca_990973.server.forclient;

import alessio_la_greca_990973.server.fortaxi.datas.statistics.TaxiStatisticsPacket;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiStatistic {

    protected int kilometers;
    protected int rides;
    @XmlElement(name = "pullutions")
    protected double pollutionAverage;
    protected int batteryLevel;

    public TaxiStatistic(){}

    public TaxiStatistic(int kilometers, int rides, double pollutionAverage, int batteryLevel){
        this.kilometers = kilometers;
        this.rides = rides;
        this.pollutionAverage = pollutionAverage;
        this.batteryLevel = batteryLevel;
    }

    public int getKilometers() {
        return kilometers;
    }

    public void setKilometers(int kilometers) {
        this.kilometers = kilometers;
    }

    public int getRides() {
        return rides;
    }

    public void setRides(int rides) {
        this.rides = rides;
    }

    public double getPollutionAverage() {
        return pollutionAverage;
    }

    public void setPollutionAverage(double pollutionAverage) {
        this.pollutionAverage = pollutionAverage;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
}
