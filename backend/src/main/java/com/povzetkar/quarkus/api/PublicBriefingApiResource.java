package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/public/briefings")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Public Briefings", description = "Public endpoints for shared briefings")
public class PublicBriefingApiResource {

    private final BriefingMapper briefingMapper;

    @Inject
    public PublicBriefingApiResource(BriefingMapper briefingMapper) {
        this.briefingMapper = briefingMapper;
    }

    @GET
    @Path("/{uuid}")
    @PermitAll
    @Operation(summary = "Get public briefing by UUID", description = "Retrieves a specific briefing by its public UUID token without authentication.")
    @APIResponse(
            responseCode = "200",
            description = "Briefing details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingDetailDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Briefing not found")
    @Parameter(name = "uuid", in = ParameterIn.PATH, required = true, description = "The public UUID of the briefing to retrieve", schema = @Schema(type = SchemaType.STRING))
    public BriefingDetailDTO getPublicBriefing(@PathParam("uuid") String uuid) {
        Briefing briefing = Briefing.findByUuidWithAssociations(uuid)
                .orElseThrow(() -> new NotFoundException("Briefing not found."));

        return briefingMapper.toDetailDTO(briefing);
    }
}
