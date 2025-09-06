package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
                                       @QueryParam("page") @DefaultValue("0") @Min(0) int page,
                                       @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size) {

        Topic topic = Topic.find("name", name).firstResult();
        if (topic == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<Channel> channels = Channel.findByTopicName(name, page, size);

        return Response.ok(channels).build();
    }
}
