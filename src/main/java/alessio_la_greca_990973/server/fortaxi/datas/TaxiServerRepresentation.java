package alessio_la_greca_990973.server.fortaxi.datas;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class TaxiServerRepresentation {
    //class to represent a taxi from the server perspective.
    //this means that this class holds only the informations regarding
    //a taxi that are of onterest of the server, that is the ID, the
    //ip address and its listening port.
    private int id;
    private String hostname;
    private int listeningPort;

    public TaxiServerRepresentation(){}

    public TaxiServerRepresentation(int id, String hostname, int listeningPort){
        this.id = id;
        this.hostname = hostname;
        this.listeningPort = listeningPort;
    }

    public int getId(){
        return id;
    }

    public void setId(int id){
        this.id = id;
    }

    public String getHostname(){
        return hostname;
    }

    public void setHostname(String hostname){
        this.hostname = hostname;
    }

    public int getListeningPort(){
        return this.listeningPort;
    }

    public void setListeningPort(int listeningPort){
        this.listeningPort = listeningPort;
    }
}
