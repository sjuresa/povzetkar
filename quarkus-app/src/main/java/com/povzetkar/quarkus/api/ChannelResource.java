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

@Path("/api/v1/users/{userId}/topics")
public class ChannelResource {

    @GET
    @Path("/{name}/channels")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getChannelsByTopic(@PathParam("userId") Long userId,
                                       @PathParam("name") @NotBlank String name,
                                       @QueryParam("page") @DefaultValue("0") @Min(0) int page,
                                       @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size) {

        List<Channel> channels = Channel.find("SELECT c FROM Channel c JOIN c.topics t WHERE t.user.id = ?1 AND t.name = ?2 ORDER BY c.name", userId, name)
                .page(page, size)
                .list();

        if (channels.isEmpty()) {
            // We must check if the topic itself exists for this user to return a proper 404.
            long topicCount = Topic.count("user.id = ?1 and name = ?2", userId, name);
            if (topicCount == 0) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }

        return Response.ok(channels).build();
    }
}
