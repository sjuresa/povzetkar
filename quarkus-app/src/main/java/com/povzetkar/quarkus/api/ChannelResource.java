package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/topics")
public class ChannelResource {

    @GET
    @Path("/{name}/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChannelsByTopic(@PathParam("name") @NotBlank String name,
                                       @QueryParam("page") @DefaultValue("0") int page,
                                       @QueryParam("size") @DefaultValue("10") int size) {

        if (Topic.count("name", name) == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<Channel> channels = Channel.find("SELECT c FROM Channel c JOIN c.topics t WHERE t.name = ?1 ORDER BY c.name", name)
                .page(page, size)
                .list();

        return Response.ok(channels).build();
    }
}
