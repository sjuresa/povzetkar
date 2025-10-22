package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ContentItemFetcher contentItemFetcher;
    private final AsyncSummarizationService asyncSummarizationService;
    private final BriefingService briefingService;
    private final ManagedExecutor managedExecutor;

    @Inject
    public OrchestratorService(ContentItemFetcher contentItemFetcher, AsyncSummarizationService asyncSummarizationService, BriefingService briefingService, ManagedExecutor managedExecutor) {
        this.contentItemFetcher = contentItemFetcher;
        this.asyncSummarizationService = asyncSummarizationService;
        this.briefingService = briefingService;
        this.managedExecutor = managedExecutor;
    }

    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public void runOrchestration() {
        logger.info("Orchestration started.");
        final LocalDate orchestrationDate = LocalDate.now();

        List<User> activeUsers = User.listAllActive();

        List<Topic> allTopics = activeUsers.stream()
                .flatMap(user -> {
                    logger.info("Processing user: {}", user.name);
                    return Topic.findByUser(user).stream();
                })
                .toList();

        List<CompletableFuture<Void>> topicFutures = allTopics.stream()
                .map(topic -> managedExecutor.runAsync(() -> {
                    logger.debug("Processing topic: {}", topic.name);

                    // 3. Fetch new content in parallel
                    List<Uni<List<ContentItem>>> fetchTasks = topic.contentSources.stream()
                            .map(contentSource -> Uni.createFrom().completionStage(managedExecutor.supplyAsync(() ->
                                    contentItemFetcher.fetchItems(contentSource, orchestrationDate, null, null)
                            )))
                            .toList();
                    List<ContentItem> newContentItems = Uni.join().all(fetchTasks)
                            .andCollectFailures()
                            .await().indefinitely()
                            .stream()
                            .filter(Objects::nonNull)  // Remove nulls from failed fetches
                            .flatMap(List::stream)
                            .toList();

                    // 4. Summarize in parallel
                    List<Uni<Void>> summarizationTasks = newContentItems.stream()
                            .map(item -> asyncSummarizationService.summarizeAsync(item)
                                    .onFailure().invoke(ex -> logger.error("Failed to summarize content item with id: {}", item.id, ex))
                                    .onFailure().recoverWithNull())
                            .toList();

                    // 5. Wait for all summarization tasks to complete
                    Uni.combine().all().unis(summarizationTasks).discardItems().await().indefinitely();

                    // 6. Generate briefing
                    briefingService.generateBriefing(topic, orchestrationDate);
                }).exceptionally(ex -> {
                    logger.error("Failed to process topic '{}' during orchestration", topic.name, ex);
                    return null; // Prevent one failure from stopping all
                }))
                .toList();

        // Wait for all topic processing to complete
        CompletableFuture.allOf(topicFutures.toArray(new CompletableFuture[0])).join();

        logger.info("Orchestration finished.");
    }
}
