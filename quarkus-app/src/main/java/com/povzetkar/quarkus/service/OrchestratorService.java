package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
    public Uni<Void> runOrchestration() {
        logger.info("Orchestration started.");
        final LocalDate orchestrationDate = LocalDate.now();

        List<User> activeUsers = User.listAllActive();
        if (activeUsers.isEmpty()) {
            logger.info("No active users found. Orchestration finished.");
            return Uni.createFrom().voidItem();
        }

        List<Topic> allTopics = Topic.findByUsers(activeUsers);

        return Multi.createFrom().iterable(allTopics)
                .onItem().transformToUni(topic -> processTopicUni(topic, orchestrationDate))
                .merge()
                .collect().asList()
                .onItem().invoke(() -> logger.info("Orchestration finished."))
                .onFailure().invoke(ex -> logger.error("Orchestration failed unexpectedly", ex))
                .replaceWithVoid();
    }

    private Uni<Void> processTopicUni(Topic topic, LocalDate orchestrationDate) {
        logger.debug("Processing topic: {}", topic.name);

        // 1. Fetch new content in parallel
        List<Uni<List<ContentItem>>> fetchTasks = topic.contentSources.stream()
                .map(contentSource -> Uni.createFrom().completionStage(managedExecutor.supplyAsync(() ->
                        contentItemFetcher.fetchItems(contentSource, orchestrationDate, null, null)
                )))
                .toList();

        return Uni.join().all(fetchTasks).andCollectFailures()
                .onItem().transform(results -> results.stream()
                        .filter(Objects::nonNull)  // Remove nulls from failed fetches
                        .flatMap(List::stream)
                        .toList())
                .onItem().transformToUni(newContentItems -> {
                    if (newContentItems.isEmpty()) {
                        logger.debug("No new content for topic '{}', skipping summarization and briefing.", topic.name);
                        return Uni.createFrom().voidItem();
                    }

                    // 2. Summarize in parallel
                    List<Uni<Void>> summarizationTasks = newContentItems.stream()
                            .map(item -> asyncSummarizationService.summarizeAsync(item)
                                    .onFailure().invoke(ex -> logger.error("Failed to summarize content item with id: {}", item.id, ex))
                                    .onFailure().recoverWithNull())
                            .toList();

                    // 3. After all summarizations, generate briefing
                    return Uni.combine().all().unis(summarizationTasks).discardItems()
                            .onItem().invoke(() -> briefingService.generateBriefing(topic, orchestrationDate));
                })
                .onFailure().invoke(ex -> logger.error("Failed to process topic '{}' during orchestration", topic.name, ex))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }
}
