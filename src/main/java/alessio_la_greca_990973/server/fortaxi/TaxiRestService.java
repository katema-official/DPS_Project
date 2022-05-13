package alessio_la_greca_990973.server.fortaxi;

import alessio_la_greca_990973.server.fortaxi.datas.TaxiRegisteredOnTheServer;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiReplyToJoin;
import alessio_la_greca_990973.server.fortaxi.datas.TaxiServerRepresentation;
import alessio_la_greca_990973.server.fortaxi.datas.statistics.TaxiStatisticsPacket;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("taxi")
public class TaxiRestService {
    //rest service for inserting a new taxi

    //service used by a taxi to add itself to the smart city
    @Path("join")
    @POST
    @Consumes({"application/json", "application/xml"})
    @Produces({"application/json", "application/xml"})
    public Response addTaxi(TaxiServerRepresentation t){
        //to add a new taxi, we must synchronize on those operation:
        //1) is the ID of this taxi already present? If so, you
        //can't add the taxi, and an error message is returned.
        //2) otherwise, add the taxi, but DO THOSE OPERATIONS TOGETHER,
        //SYNCHRONIZED! Otherwise we could have inconsistencies.
        //this synchronization is performed inside the add() method, on the instance
        //of the TaxiRegisteredOnTheServer.
        boolean success = TaxiRegisteredOnTheServer.getInstance().add(t);
        if(success) {
            int id = t.getId();
            TaxiReplyToJoin trtj = new TaxiReplyToJoin(id);
            return Response.ok(trtj).build();
        }else{
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
        }
    }

    //service used by a taxi to delete itself from the smart city
    @Path("leave")
    @DELETE
    public Response deleteTaxi(@QueryParam("id") int id){
        //to delete a taxi from the smart city, we simply receive the ID, delete the
        //corrisponding taxi from the data structure and respond with an ok message.
        //Even if in this project we shouldn't have incoming delete requests with
        //unexisting IDs, following the "defensive programming" technique, I'll handle
        //also the cases in which the ID is not present in the server data structure.
        //the synchronization, as before, is provided in the delete() method.
        boolean success = TaxiRegisteredOnTheServer.getInstance().delete(id);
        if(success){
            return Response.ok().build();
        }else{
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    //service used by a taxi to add new statistics
    @Path("append")
    @POST
    public Response appendStatistic(TaxiStatisticsPacket packet){

    }

}
