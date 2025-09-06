package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Topic;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/topics")
public class ChannelResource {

    @GET
    @Path("/{name}/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChannelsByTopic(@PathParam("name") @NotBlank String name) {
        Topic topic = Topic.find("from Topic t left join fetch t.channels where t.name = ?1", name).firstResult();
        if (topic == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(topic.channels).build();
    }
}
