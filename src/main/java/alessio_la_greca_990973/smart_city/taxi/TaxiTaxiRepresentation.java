package alessio_la_greca_990973.smart_city.taxi;

import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;

public class TaxiTaxiRepresentation extends TaxiServerRepresentation {

    private int currX;
    private int currY;

    public TaxiTaxiRepresentation(int id, String hostname, int listeningPort, int currX, int currY){
        super(id, hostname, listeningPort);
        this.currX = currX;
        this.currY = currY;
    }

    public int getCurrX() {
        return currX;
    }

    public void setCurrX(int currX) {
        this.currX = currX;
    }

    public int getCurrY() {
        return currY;
    }

    public void setCurrY(int currY) {
        this.currY = currY;
    }
}
