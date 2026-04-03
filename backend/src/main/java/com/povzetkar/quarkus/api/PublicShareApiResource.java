package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.PublicInsightType;
import com.povzetkar.quarkus.api.dto.PublicShareDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.api.mapper.SummaryMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
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

import java.util.Optional;

@Path("/api/v1/public/resolve")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Public Insight Resolver", description = "Unified endpoint for resolving shared briefings and summaries")
public class PublicShareApiResource {

    private final BriefingMapper briefingMapper;
    private final SummaryMapper summaryMapper;

    @Inject
    public PublicShareApiResource(BriefingMapper briefingMapper, SummaryMapper summaryMapper) {
        this.briefingMapper = briefingMapper;
        this.summaryMapper = summaryMapper;
    }

    @GET
    @Path("/{uuid}")
    @PermitAll
    @Operation(summary = "Resolve shared content by UUID", description = "Retrieves either a Briefing or a Summary based on the public UUID token.")
    @APIResponse(
            responseCode = "200",
            description = "Content resolved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicShareDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content not found")
    @Parameter(name = "uuid", in = ParameterIn.PATH, required = true, description = "The public UUID of the shared content", schema = @Schema(type = SchemaType.STRING))
    public PublicShareDTO resolveContent(@PathParam("uuid") String uuid) {
        // Step 1: Lightweight lookup for Briefing ID
        Optional<Long> briefingId = Briefing.find("uuid = ?1", uuid)
                .project(Long.class)
                .firstResultOptional();
        
        if (briefingId.isPresent()) {
            Briefing briefing = Briefing.findByIdWithAssociations(briefingId.get())
                    .orElseThrow(() -> new NotFoundException("Briefing no longer available."));
            return new PublicShareDTO(PublicInsightType.BRIEFING, briefingMapper.toDetailDTO(briefing));
        }

        // Step 2: Lightweight lookup for Summary ID
        Optional<Long> summaryId = Summary.find("uuid = ?1", uuid)
                .project(Long.class)
                .firstResultOptional();
        
        if (summaryId.isPresent()) {
            Summary summary = Summary.findByIdWithAssociations(summaryId.get())
                    .orElseThrow(() -> new NotFoundException("Summary no longer available."));
            return new PublicShareDTO(PublicInsightType.SUMMARY, summaryMapper.toPublicDTO(summary));
        }

        throw new NotFoundException("Shared content not found or no longer public.");
    }
}
