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

    private int kilometers;
    private int rides;
    @XmlElement(name = "pullutions")
    private List<Double> pollutionAverages;
    private int batteryLevel;

    public TaxiStatistic(){}

    public TaxiStatistic(int kilometers, int rides, List<Double> pollutionAverages, int batteryLevel){
        this.kilometers = kilometers;
        this.rides = rides;
        this.pollutionAverages = pollutionAverages;
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

    public List<Double> getPollutionAverages() {
        return pollutionAverages;
    }

    public void setPollutionAverages(List<Double> pollutionAverages) {
        this.pollutionAverages = pollutionAverages;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
}
