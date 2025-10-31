package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@ApplicationScoped
public class AsyncSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncSummarizationService.class);

    private final SummarizationWorker worker;
    private final ManagedExecutor managedExecutor;

    @Inject
    public AsyncSummarizationService(SummarizationWorker worker, ManagedExecutor managedExecutor) {
        this.worker = worker;
        this.managedExecutor = managedExecutor;
    }

    public Uni<Void> summarizeAsync(ContentItem item, LocalDate orchestrationDate) {
        logger.info("Queuing summarization for content item: '{}' by {} published at {}", item.title, item.contentSource.name, item.publishedAt);
        return Uni.createFrom().completionStage(managedExecutor.runAsync(() -> worker.summarize(item, orchestrationDate)));
    }
}
