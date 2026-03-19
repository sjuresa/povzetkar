package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.dto.SseEventDTO;
import com.povzetkar.quarkus.service.event.JobStatusChangedEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@ApplicationScoped
public class EventBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(EventBroadcaster.class);
    private final BroadcastProcessor<SseEventDTO> processor = BroadcastProcessor.create();

    public Multi<SseEventDTO> getEventStream() {
        return Multi.createBy().merging()
                .streams(
                        Multi.createFrom().item(SseEventDTO.of("CONNECTED", "Stream established")),
                        processor,
                        Multi.createFrom().ticks().every(Duration.ofSeconds(30))
                                .map(tick -> SseEventDTO.of("PULSE", "Still alive"))
                );
    }

    public void onJobStatusChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) JobStatusChangedEvent event) {
        logger.debug("Broadcasting job status change for job {}: {}", event.jobId(), event.status());
        broadcast(SseEventDTO.of("JOB_STATUS_CHANGED", event));
    }

    public void broadcast(SseEventDTO event) {
        processor.onNext(event);
    }

    @PreDestroy
    void shutdown() {
        logger.info("Shutting down EventBroadcaster and completing stream.");
        processor.onComplete();
    }
}
