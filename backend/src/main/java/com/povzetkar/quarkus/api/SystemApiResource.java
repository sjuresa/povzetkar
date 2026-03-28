package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.SystemMetadataDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import com.povzetkar.quarkus.service.UserContextService;

import java.time.Clock;
import java.time.LocalDate;

@Path("/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Enveloped
@Tag(name = "System", description = "System-wide metadata and health")
public class SystemApiResource extends BaseApiResource {

    private static final String STATUS_ACTIVE_EVOLUTION = "ACTIVE_EVOLUTION";
    
    private final String version;
    private final String environment;
    private final Clock clock;

    @Inject
    public SystemApiResource(
            UserContextService userContextService,
            @ConfigProperty(name = "quarkus.application.version", defaultValue = "0.0.0-SNAPSHOT") String version,
            @ConfigProperty(name = "quarkus.profile", defaultValue = "dev") String environment,
            Clock clock) {
        super(userContextService);
        this.version = version;
        this.environment = environment;
        this.clock = clock;
    }

    @GET
    @Path("/metadata")
    @PermitAll
    @Operation(summary = "Get system metadata", description = "Returns current version, status and release info.")
    public SystemMetadataDTO getMetadata() {
        return new SystemMetadataDTO(
                version,
                environment,
                STATUS_ACTIVE_EVOLUTION,
                LocalDate.now(clock).toString()
        );
    }
}
