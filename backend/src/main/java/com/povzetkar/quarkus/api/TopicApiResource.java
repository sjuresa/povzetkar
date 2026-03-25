package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPaginatedResponse;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.Idempotent;
import com.povzetkar.quarkus.api.filter.OptimisticLocking;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.mapper.TopicMapper;
import com.povzetkar.quarkus.api.util.QueryParser;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.security.Permission;
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
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/v1/topics")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Topics", description = "Endpoints for managing Topics")
public class TopicApiResource extends BaseApiResource {

    private static final String FIND_BY_USER_AND_NAME_QUERY = "user = ?1 and name = ?2";
    private static final String DUPLICATE_TOPIC_NAME = "A topic with this name already exists.";
    private static final String TOPIC_WITH_ID = "Topic with id ";
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
    @RolesAllowed(Permission.V_TOPIC_VIEW)
    @Operation(summary = "Get all topics", description = "Retrieves a paginated list of topics for the current user.")
    @APIResponse(
            responseCode = "200",
            description = "List of topics retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicPaginatedResponse.class))
    )
    public TopicPaginatedResponse getTopics(
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        User user = getAuthenticatedUser();
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("t.id"), Set.of("id", "name", "createdAt", "updatedAt", "lastRunDate", "enabled"), "t");
        PanacheQuery<Topic> query = Topic.find("select distinct t from Topic t left join fetch t.contentSources where t.user = ?1", panacheSort, user);
        long count = query.count();
        List<TopicDTO> dtoList = query.page(pageIndex, clampedPageSize)
                .list()
                .stream()
                .map(topicMapper::toDTO)
                .toList();

        return new TopicPaginatedResponse(dtoList, count, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(Permission.V_TOPIC_VIEW)
    @Operation(summary = "Get topic by ID", description = "Retrieves a specific topic by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Topic retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Topic not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the topic to retrieve", schema = @Schema(type = SchemaType.INTEGER))
    public TopicDTO getTopic(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);
        return topicMapper.toDTO(topic);
    }

    @POST
    @Transactional
    @RolesAllowed(Permission.V_TOPIC_CREATE)
    @APIResponse(
            responseCode = "201",
            description = "Topic created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicDTO.class))
    )
    public Response createTopic(@Valid @NotNull TopicCreateDTO createDTO, @Context UriInfo uriInfo) {
        User user = getAuthenticatedUser();

        checkForDuplicateTopicName(user, createDTO.name());

        Topic topic = topicMapper.toEntity(createDTO);
        topic.user = user;
        
        if (createDTO.enabled() != null) {
            topic.enabled = createDTO.enabled();
        }

        if (createDTO.sourceIds() != null && !createDTO.sourceIds().isEmpty()) {
            topic.contentSources = resolveSources(createDTO.sourceIds());
        }

        topic.persist();

        URI uri = uriInfo.getAbsolutePathBuilder().path(Long.toString(topic.id)).build();
        return Response.created(uri)
                .type(MediaType.APPLICATION_JSON)
                .entity(topicMapper.toDTO(topic))
                .build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed(Permission.V_TOPIC_UPDATE)
    @APIResponse(
            responseCode = "200",
            description = "Topic updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopicDTO.class))
    )
    @OptimisticLocking
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the topic to update", schema = @Schema(type = SchemaType.INTEGER))
    public TopicDTO patchTopic(
            @PathParam("id") Long id,
            @HeaderParam("If-Match") String ifMatch,
            @Valid @NotNull TopicPatchDTO patchDTO) {

        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        checkOptimisticLock(topic, ifMatch);

        if (patchDTO.name() != null && !topic.name.equals(patchDTO.name())) {
            checkForDuplicateTopicName(user, patchDTO.name());
        }

        topicMapper.patchEntity(patchDTO, topic);

        if (patchDTO.sourceIds() != null) {
            topic.contentSources.clear();
            if (!patchDTO.sourceIds().isEmpty()) {
                topic.contentSources.addAll(resolveSources(patchDTO.sourceIds()));
            }
        }

        return topicMapper.toDTO(topic);
    }

    private Set<ContentSource> resolveSources(Set<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return new java.util.HashSet<>();
        }
        List<ContentSource> sources = ContentSource.list("id in ?1", sourceIds);
        if (sources.size() != sourceIds.size()) {
            Set<Long> foundIds = sources.stream().map(s -> s.id).collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(sourceIds);
            missingIds.removeAll(foundIds);
            throw new jakarta.ws.rs.NotFoundException("ContentSource(s) not found: " + missingIds);
        }
        return new HashSet<>(sources);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed(Permission.V_TOPIC_DELETE)
    @OptimisticLocking
    @Operation(summary = "Delete topic", description = "Deletes a specific topic.")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the topic to delete", schema = @Schema(type = SchemaType.INTEGER))
    @Parameter(name = "If-Match", in = ParameterIn.HEADER, required = false, description = "Optimistic locking version", schema = @Schema(type = SchemaType.STRING))
    public Response deleteTopic(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);
        
        checkOptimisticLock(topic, ifMatch);
        
        topic.delete();
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/commands/generate-summaries")
    @RolesAllowed(Permission.V_TOPIC_TRIGGER_RUN)
    @Operation(summary = "Trigger topic-only summarization", description = "Triggers a background job to fetch and summarize content for a specific topic today without creating a briefing.")
    @APIResponse(responseCode = "202", description = "Job triggered successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobDTO.class)))
    @APIResponse(responseCode = "404", description = "Topic not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the topic to summarize", schema = @Schema(type = SchemaType.INTEGER))
    public BackgroundJobDTO triggerTopicSummaries(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Topic topic = Topic.<Topic>findByIdOptional(id)
                .filter(t -> t.user.id.equals(user.id))
                .orElseThrow(() -> new NotFoundException(TOPIC_WITH_ID + id + " not found."));

        BackgroundJob job = backgroundJobService.triggerTopicSummariesJob(topic, user.id);
        return backgroundJobMapper.toDTO(job);
    }

    @POST
    @Path("/{id}/commands/generate-briefing")
    @RolesAllowed(Permission.V_TOPIC_TRIGGER_RUN)
    @Idempotent
    @Operation(summary = "Trigger Topic run", description = "Manually triggers a briefing generation for the topic.")
    @APIResponse(
            responseCode = "202",
            description = "Briefing generation triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Topic not found")
    @APIResponse(responseCode = "409", description = "Conflict or already running")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the topic to trigger", schema = @Schema(type = SchemaType.INTEGER))
    public Response triggerTopicRun(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        User user = getAuthenticatedUser();
        Topic topic = findResolvedTopic(id, user);

        var job = backgroundJobService.triggerTopicBriefingJob(topic, user.id);

        URI location = uriInfo.getBaseUriBuilder()
                .path(JobApiResource.class)
                .path(JobApiResource.class, "getJobById")
                .build(job.id);

        return Response.accepted()
                .location(location)
                .entity(backgroundJobMapper.toDTO(job))
                .build();
    }

    @GET
    @Path("/default-prompt-template")
    @RolesAllowed(Permission.V_TOPIC_VIEW)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get default prompt template", description = "Retrieves the global default prompt template used for topics.")
    public java.util.Map<String, String> getDefaultPromptTemplate() {
        return java.util.Map.of("template", com.povzetkar.quarkus.ai.Prompts.SUMMARIES_SUMMARY_PROMPT);
    }

    private void checkForDuplicateTopicName(User user, String name) {
        Optional<Topic> existing = Topic.find(FIND_BY_USER_AND_NAME_QUERY, user, name).firstResultOptional();
        if (existing.isPresent()) {
            throw new WebApplicationException(DUPLICATE_TOPIC_NAME, Response.Status.CONFLICT);
        }
    }

    private Topic findResolvedTopic(Long id, User user) {
        return Topic.<Topic>find("from Topic t left join fetch t.contentSources where t.id = ?1 and t.user = ?2", id, user)
                .firstResultOptional()
                .orElseThrow(
                        () -> new NotFoundException(TOPIC_WITH_ID + id + NOT_FOUND_SUFFIX));
    }

}
