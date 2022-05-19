package alessio_la_greca_990973.server.fortaxi.datas.statistics;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiStatisticsPacket {

    private int kilometers;
    private int rides;
    @XmlElement(name = "pollutionAverages")
    private List<Double> pollutionAverages;
    private int taxiId;
    private double timestamp;
    private int batteryLevel;

    public TaxiStatisticsPacket(){}

    public TaxiStatisticsPacket(int kilometers, int rides, List<Double> pollutionAverages, int taxiId, double timestamp, int batteryLevel){
        this.kilometers = kilometers;
        this.rides = rides;
        this.pollutionAverages = pollutionAverages;
        this.taxiId = taxiId;
        this.timestamp = timestamp;
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

    public int getTaxiId() {
        return taxiId;
    }

    public void setTaxiId(int taxiId) {
        this.taxiId = taxiId;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
}
