package alessio_la_greca_990973.server.fortaxi.datas;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiReplyToJoin {

    @XmlElement(name = "otherTaxis")
    List<TaxiServerRepresentation> currentTaxis;

    private int startingX;
    private int startingY;

    public TaxiReplyToJoin(){}

    public TaxiReplyToJoin(int myId){

        synchronized (TaxiRegisteredOnTheServer.getInstance()) {
            currentTaxis = TaxiRegisteredOnTheServer.getInstance().getActualTaxis();
            TaxiServerRepresentation impostor = null;
            Iterator it = currentTaxis.iterator();
            while (it.hasNext()) {
                TaxiServerRepresentation t = (TaxiServerRepresentation) it.next();
                if (t.getId() == myId) {
                    impostor = t;
                    //I can't remove here the "impostor" since we can't modify a collection while we're iterating it
                }
            }
            currentTaxis.remove(impostor);

            Random rand = new Random();
            int n = rand.nextInt(2);
            startingX = n == 0 ? 0 : 9;
            n = rand.nextInt(2);
            startingY = n == 0 ? 0 : 9;
        }
    }

    public List<TaxiServerRepresentation> getCurrentTaxis() {
        return currentTaxis;
    }

    public int getStartingX() {
        return startingX;
    }

    public int getStartingY() {
        return startingY;
    }

    public void setCurrentTaxis(List<TaxiServerRepresentation> currentTaxis) {
        this.currentTaxis = currentTaxis;
    }

    public void setStartingX(int startingX) {
        this.startingX = startingX;
    }

    public void setStartingY(int startingY) {
        this.startingY = startingY;
    }
}
