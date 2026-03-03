package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.dto.TopicUpdateDTO;
import com.povzetkar.quarkus.api.mapper.TopicMapper;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.OrchestrationManager;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/topics")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Topics", description = "Endpoints for managing Topics")
public class TopicApiResource {

    public static final String USER_MUST_BE_AUTHENTICATED = "User must be authenticated";
    public static final String USER_1_AND_NAME_2 = "user = ?1 and name = ?2";
    private final TopicMapper topicMapper;
    private final UserContextService userContextService;
    private final OrchestrationManager orchestrationManager;
    private final Clock clock;

    @Inject
    public TopicApiResource(TopicMapper topicMapper, UserContextService userContextService,
            OrchestrationManager orchestrationManager, Clock clock) {
        this.topicMapper = topicMapper;
        this.userContextService = userContextService;
        this.orchestrationManager = orchestrationManager;
        this.clock = clock;
    }

    @GET
    @RolesAllowed("TOPIC_VIEW")
    @Operation(summary = "Get all topics", description = "Retrieves a paginated list of topics for the current user.")
    public PaginatedResponse<TopicDTO> getTopics(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        User user = getAuthenticatedUser();

        PanacheQuery<Topic> query = Topic.find("user", Sort.by("id"), user);
        query.page(pageIndex, pageSize);

        List<TopicDTO> dtoList = query.stream().map(topicMapper::toDTO).toList();
        long count = query.count();

        return new PaginatedResponse<>(dtoList, count);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("TOPIC_VIEW")
    @Operation(summary = "Get topic by ID", description = "Retrieves a specific topic by its ID.")
    public TopicDTO getTopic(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);
        return topicMapper.toDTO(topic);
    }

    @POST
    @Transactional
    @RolesAllowed("TOPIC_CREATE")
    @Operation(summary = "Create topic", description = "Creates a new topic for the current user.")
    public Response createTopic(@Valid @NotNull TopicCreateDTO createDTO) {
        User user = getAuthenticatedUser();

        checkForDuplicateTopicName(user, createDTO.name());

        Topic topic = topicMapper.toEntity(createDTO);
        topic.user = user;
        topic.persist();

        return Response.created(URI.create("/api/v1/topics/" + topic.id)).entity(topicMapper.toDTO(topic)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("TOPIC_UPDATE")
    @Operation(summary = "Update topic", description = "Fully replaces an existing topic.")
    public TopicDTO updateTopic(@PathParam("id") Long id, @Valid @NotNull TopicUpdateDTO updateDTO) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        // Prevent duplicate names for the same user when changing name
        if (!topic.name.equals(updateDTO.name())) {
            checkForDuplicateTopicName(user, updateDTO.name());
        }

        topicMapper.updateEntity(updateDTO, topic);
        return topicMapper.toDTO(topic);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed("TOPIC_UPDATE")
    @Operation(summary = "Partially update topic", description = "Partially updates an existing topic.")
    public TopicDTO patchTopic(@PathParam("id") Long id, @Valid @NotNull TopicPatchDTO patchDTO) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        if (patchDTO.name() != null && !topic.name.equals(patchDTO.name())) {
            checkForDuplicateTopicName(user, patchDTO.name());
        }

        topicMapper.patchEntity(patchDTO, topic);
        return topicMapper.toDTO(topic);
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
    @Path("/{id}/trigger-run")
    @RolesAllowed("TOPIC_TRIGGER_RUN")
    @Operation(summary = "Trigger Topic run", description = "Manually triggers a briefing generation for the topic.")
    public Response triggerTopicRun(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        // Run the orchestration process in the background
        orchestrationManager.runOrchestrationForTopicManually(topic, java.time.LocalDate.now(clock))
                .subscribeAsCompletionStage();

        return Response.accepted().build();
    }

    private void checkForDuplicateTopicName(User user, String name) {
        Optional<Topic> existing = Topic.find(USER_1_AND_NAME_2, user, name).firstResultOptional();
        if (existing.isPresent()) {
            throw new WebApplicationException("A topic with this name already exists.", Response.Status.CONFLICT);
        }
    }

    private Topic findResolvedTopic(Long id, User user) {
        return Topic.<Topic>find("id = ?1 and user = ?2", id, user)
                .firstResultOptional()
                .orElseThrow(
                        () -> new NotFoundException("Topic with id " + id + " not found or does not belong to user."));
    }

    private User getAuthenticatedUser() {
        return userContextService.getCurrentUser()
                .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));
    }
}
