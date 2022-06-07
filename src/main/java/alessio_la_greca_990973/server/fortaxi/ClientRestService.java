package alessio_la_greca_990973.server.fortaxi;

import alessio_la_greca_990973.server.forclient.TaxiStatistic;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiRegisteredOnTheServer;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.ArrayList;

@Path("client")
public class ClientRestService {

    private final boolean DEBUG_LOCAL = true;

    @Path("taxis")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getTaxis(){
        ArrayList<TaxiServerRepresentation> res = TaxiRegisteredOnTheServer.getInstance().getActualTaxis();
        return Response.ok(res).build();

    }

    @Path("stats/{id}/{n}")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getLastNStatistics(@PathParam("id") int id, @PathParam("n") int n){
        TaxiStatistic ts = TaxiRegisteredOnTheServer.getInstance().getLastNStatisticOfTaxi(id, n);
        return Response.ok(ts).build();
    }

    @Path("timestamps/{t1}/{t2}")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getStatisticsBetweenTimestamps(@PathParam("t1") double t1, @PathParam("t2") double t2){
        TaxiStatistic ts = TaxiRegisteredOnTheServer.getInstance().getGlobalStatisticsBetweenTimestamps(t1, t2);
        return Response.ok(ts).build();
    }

}
