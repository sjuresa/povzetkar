package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ContentItemFetcher contentItemFetcher;
    private final SummarizationService summarizationService;
    private final BriefingService briefingService;

    @Inject
    public OrchestratorService(ContentItemFetcher contentItemFetcher, SummarizationService summarizationService, BriefingService briefingService) {
        this.contentItemFetcher = contentItemFetcher;
        this.summarizationService = summarizationService;
        this.briefingService = briefingService;
    }

    @Transactional
    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public void runOrchestration() {
        logger.info("Orchestration started.");

        List<User> activeUsers = User.listAllActive();

        for (User user : activeUsers) {
            logger.info("Processing user: {}", user.name);
            List<Topic> userTopics = Topic.findByUser(user);

            for (Topic topic : userTopics) {
                logger.debug("Processing topic: {}", topic.name);

                // 3. Fetch new content
                List<ContentItem> newContentItems = topic.contentSources.stream()
                        .flatMap(contentSource -> contentItemFetcher.fetchItems(contentSource, LocalDate.now(), null, null).stream())
                        .toList();

                // 4. Summarize in parallel
                List<CompletableFuture<Void>> summarizationTasks = newContentItems.stream()
                        .map(item -> CompletableFuture.runAsync(() -> summarizationService.summarizeVideo(item)))
                        .toList();

                // 5. Wait for all summarization tasks to complete
                CompletableFuture.allOf(summarizationTasks.toArray(new CompletableFuture[0])).join();

                // 6. Generate briefing
                briefingService.generateBriefing(topic, LocalDate.now());
            }
        }
        logger.info("Orchestration finished.");
    }
}
