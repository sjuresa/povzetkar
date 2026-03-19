package com.povzetkar.quarkus.service;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

@ApplicationScoped
@Unremovable
public class ClockProducer {

    @Produces
    @Singleton
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
