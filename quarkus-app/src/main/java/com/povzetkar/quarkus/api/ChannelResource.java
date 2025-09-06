package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import jakarta.transaction.Transactional;
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
    @Transactional
    public Response getChannelsByTopic(@PathParam("name") @NotBlank String name,
                                       @QueryParam("page") @DefaultValue("0") @Min(0) int page,
                                       @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size) {

        List<Channel> channels = Channel.findByTopicName(name, page, size);
        // If no channels are found, we must check if the topic exists at all
        // to return a 404, otherwise we'll correctly return a 200 with an empty list.
        if (channels.isEmpty() && Topic.count("name", name) == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(channels).build();
    }
}
