package alessio_la_greca_990973.server.datas;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
public class TaxiReplyToJoin {

    @XmlElement(name = "otherTaxis")
    List<TaxiRegisteredOnTheServer> currentTaxis;

    private int startingX;
    private int startingY;

    public TaxiReplyToJoin(){}

    public TaxiReplyToJoin(int myId, HashMap<Integer, TaxiServerRepresentation> allTaxis){

        synchronized (TaxiRegisteredOnTheServer.getInstance()) {
            currentTaxis = new ArrayList<TaxiRegisteredOnTheServer>();
            Set entrySet = allTaxis.entrySet();
            Iterator it = entrySet.iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                if (!entry.getKey().equals(myId)) {
                    currentTaxis.add((TaxiRegisteredOnTheServer) entry.getValue());
                }
            }

            Random rand = new Random();
            int n = rand.nextInt(2);
            startingX = n == 0 ? 0 : 9;
            n = rand.nextInt(2);
            startingY = n == 0 ? 0 : 9;
        }
    }

    //TODO

}
