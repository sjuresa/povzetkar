package com.povzetkar.quarkus.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/q/health")
public class HealthCheckResource {

    @GET
    public Response health() {
        return Response.ok("Healthy am I. :)").build();
    }
}
