package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;

@ApplicationScoped
public class SummarizationService {

    private final SummarizationWorker worker;

    @Inject
    public SummarizationService(SummarizationWorker worker) {
        this.worker = worker;
    }

    public boolean summarizeVideo(ContentItem item, LocalDate orchestrationDate) {
        return worker.summarize(item, orchestrationDate);
    }
}
