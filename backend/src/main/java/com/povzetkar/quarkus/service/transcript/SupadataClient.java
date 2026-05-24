package com.povzetkar.quarkus.service.transcript;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "supadata")
@ClientHeaderParam(name = "x-api-key", value = "${SUPADATA_API_KEY}")
@Path("/v1")
public interface SupadataClient {

    @GET
    @Path("/transcript")
    Response getTranscript(
            @QueryParam("url") String url,
            @QueryParam("lang") String lang,
            @QueryParam("text") boolean text);

    @GET
    @Path("/transcript/{jobId}")
    SupadataJobStatusResponse getJobStatus(@PathParam("jobId") String jobId);
}
