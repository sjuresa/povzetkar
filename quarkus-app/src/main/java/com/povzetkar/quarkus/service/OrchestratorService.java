package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ContentItemFetcher contentItemFetcher;
    private final AsyncSummarizationService asyncSummarizationService;
    private final BriefingService briefingService;

    @Inject
    public OrchestratorService(ContentItemFetcher contentItemFetcher, AsyncSummarizationService asyncSummarizationService, BriefingService briefingService) {
        this.contentItemFetcher = contentItemFetcher;
        this.asyncSummarizationService = asyncSummarizationService;
        this.briefingService = briefingService;
    }

    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public void runOrchestration() {
        logger.info("Orchestration started.");
        final LocalDate orchestrationDate = LocalDate.now();

        List<User> activeUsers = User.listAllActive();

        for (User user : activeUsers) {
            logger.info("Processing user: {}", user.name);
            List<Topic> userTopics = Topic.findByUser(user);

            for (Topic topic : userTopics) {
                logger.debug("Processing topic: {}", topic.name);

                // 3. Fetch new content
                List<ContentItem> newContentItems = topic.contentSources.stream()
                        .flatMap(contentSource -> contentItemFetcher.fetchItems(contentSource, orchestrationDate, null, null).stream())
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
            }
        }
        logger.info("Orchestration finished.");
    }
}
