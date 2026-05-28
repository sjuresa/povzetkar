package com.povzetkar.quarkus.service.transcript;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "transcript-proxy")
@ClientHeaderParam(name = "X-API-Key", value = "${TRANSCRIPT_PROXY_API_KEY:}")
@Path("/transcripts")
public interface TranscriptProxyClient {

    @GET
    @Path("/{videoId}")
    Response getTranscript(@PathParam("videoId") String videoId);
}
