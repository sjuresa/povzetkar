package com.povzetkar.quarkus.service.transcript;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "transcript-proxy")
@Path("/transcripts")
public interface TranscriptProxyClient {

    @GET
    @Path("/{videoId}")
    Response getTranscript(@PathParam("videoId") String videoId, @HeaderParam("X-API-Key") String apiKey);
}
