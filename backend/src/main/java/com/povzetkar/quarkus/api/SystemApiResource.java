package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.SystemMetadataDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Path("/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "System", description = "System-wide metadata and health")
public class SystemApiResource extends BaseApiResource {

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "0.0.0-SNAPSHOT")
    String version;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "dev")
    String profile;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @GET
    @Path("/metadata")
    @PermitAll
    @Operation(summary = "Get system metadata", description = "Returns current version, status and release info.")
    public SystemMetadataDTO getMetadata() {
        return new SystemMetadataDTO(
            version,
            profile,
            "ACTIVE_EVOLUTION",
            LocalDate.now().format(DATE_FORMATTER) // Refined for best practices
        );
    }
}
