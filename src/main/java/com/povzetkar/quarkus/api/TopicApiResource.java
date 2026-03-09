package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.mapper.TopicMapper;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/topics")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Topics", description = "Endpoints for managing Topics")
public class TopicApiResource extends BaseApiResource {

    private static final String FIND_BY_USER_AND_NAME_QUERY = "user = ?1 and name = ?2";
    private static final String DUPLICATE_TOPIC_NAME = "A topic with this name already exists.";
    private static final String TOPIC_NOT_FOUND = "Topic with id ";
    private static final String NOT_FOUND_SUFFIX = " not found or does not belong to user.";
    
    private final TopicMapper topicMapper;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;

    @Inject
    public TopicApiResource(TopicMapper topicMapper, BackgroundJobService backgroundJobService, BackgroundJobMapper backgroundJobMapper, UserContextService userContextService) {
        super(userContextService);
        this.topicMapper = topicMapper;
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
    }

    @GET
    @RolesAllowed("TOPIC_VIEW")
    @Operation(summary = "Get all topics", description = "Retrieves a paginated list of topics for the current user.")
    @APIResponse(
            responseCode = "200",
            description = "List of topics retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicListEnvelope.class))
    )
    public ApiEnvelopeDTO<List<TopicDTO>> getTopics(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        User user = getAuthenticatedUser();

        PanacheQuery<Topic> query = Topic.find("user", Sort.by("id"), user);
        query.page(pageIndex, pageSize);

        List<TopicDTO> dtoList = query.stream().map(topicMapper::toDTO).toList();
        long count = query.count();

        return ApiEnvelopeDTO.paginated(dtoList, count, pageIndex, pageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("TOPIC_VIEW")
    @Operation(summary = "Get topic by ID", description = "Retrieves a specific topic by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Topic retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Topic not found")
    public ApiEnvelopeDTO<TopicDTO> getTopic(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);
        return ApiEnvelopeDTO.success(topicMapper.toDTO(topic));
    }

    @POST
    @Transactional
    @RolesAllowed("TOPIC_CREATE")
    @APIResponse(
            responseCode = "201",
            description = "Topic created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicEnvelope.class))
    )
    public Response createTopic(@Valid @NotNull TopicCreateDTO createDTO) {
        User user = getAuthenticatedUser();

        checkForDuplicateTopicName(user, createDTO.name());

        Topic topic = topicMapper.toEntity(createDTO);
        topic.user = user;
        topic.persist();

        return Response.created(URI.create("/api/v1/topics/" + topic.id))
                .entity(ApiEnvelopeDTO.success(topicMapper.toDTO(topic)))
                .build();
    }


    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed("TOPIC_UPDATE")
    @APIResponse(
            responseCode = "200",
            description = "Topic updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicEnvelope.class))
    )
    public ApiEnvelopeDTO<TopicDTO> patchTopic(@PathParam("id") Long id, @Valid @NotNull TopicPatchDTO patchDTO) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        if (patchDTO.name() != null && !topic.name.equals(patchDTO.name())) {
            checkForDuplicateTopicName(user, patchDTO.name());
        }

        topicMapper.patchEntity(patchDTO, topic);
        return ApiEnvelopeDTO.success(topicMapper.toDTO(topic));
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("TOPIC_DELETE")
    @Operation(summary = "Delete topic", description = "Deletes a specific topic.")
    public Response deleteTopic(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);
        topic.delete();
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/commands/generate-briefing")
    @RolesAllowed("TOPIC_TRIGGER_RUN")
    @Operation(summary = "Trigger Topic run", description = "Manually triggers a briefing generation for the topic.")
    @APIResponse(
            responseCode = "202",
            description = "Briefing generation triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = JobEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Topic not found")
    @APIResponse(responseCode = "409", description = "Conflict or already running")
    public Response triggerTopicRun(@PathParam("id") Long id, @jakarta.ws.rs.core.Context jakarta.ws.rs.core.UriInfo uriInfo) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        var job = backgroundJobService.triggerTopicBriefingJob(topic, user.id);

        URI location = uriInfo.getBaseUriBuilder()
                .path(JobApiResource.class)
                .path(JobApiResource.class, "getJobById")
                .build(job.id);

        return Response.accepted()
                .location(location)
                .entity(ApiEnvelopeDTO.success(backgroundJobMapper.toDTO(job)))
                .build();
    }

    private void checkForDuplicateTopicName(User user, String name) {
        Optional<Topic> existing = Topic.find(FIND_BY_USER_AND_NAME_QUERY, user, name).firstResultOptional();
        if (existing.isPresent()) {
            throw new WebApplicationException(DUPLICATE_TOPIC_NAME, Response.Status.CONFLICT);
        }
    }

    private Topic findResolvedTopic(Long id, User user) {
        return Topic.<Topic>find("id = ?1 and user = ?2", id, user)
                .firstResultOptional()
                .orElseThrow(
                        () -> new NotFoundException(TOPIC_NOT_FOUND + id + NOT_FOUND_SUFFIX));
    }

    // Helper classes for OpenAPI documentation
    private static class JobEnvelope extends ApiEnvelopeDTO<BackgroundJobDTO> {}
    private static class TopicEnvelope extends ApiEnvelopeDTO<TopicDTO> {}
    private static class TopicListEnvelope extends ApiEnvelopeDTO<List<TopicDTO>> {}
}
