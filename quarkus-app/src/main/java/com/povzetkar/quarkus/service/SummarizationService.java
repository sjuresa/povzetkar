package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SummarizationService {

    private final SummarizationWorker worker;

    @Inject
    public SummarizationService(SummarizationWorker worker) {
        this.worker = worker;
    }

    public boolean summarizeVideo(ContentItem item) {
        return worker.summarize(item);
    }
}
