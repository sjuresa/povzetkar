package com.povzetkar.quarkus.service.transcript;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.YearMonth;
import java.time.ZoneOffset;

@ApplicationScoped
public class TranscriptProviderQuotaService {

    private final EntityManager em;

    @Inject
    public TranscriptProviderQuotaService(EntityManager em) {
        this.em = em;
    }

    @Transactional(TxType.REQUIRES_NEW)
    public void recordFetch(String providerName) {
        String period = YearMonth.now(ZoneOffset.UTC).toString();
        int updated = em.createNativeQuery(
                        "INSERT INTO transcript_provider_quota_usage (provider_name, period_yyyy_mm, fetches_count) " +
                        "VALUES (:provider, :period, 1) " +
                        "ON CONFLICT (provider_name, period_yyyy_mm) " +
                        "DO UPDATE SET fetches_count = transcript_provider_quota_usage.fetches_count + 1")
                .setParameter("provider", providerName)
                .setParameter("period", period)
                .executeUpdate();
        if (updated == 0) {
            throw new IllegalStateException("Quota upsert affected 0 rows for provider: " + providerName);
        }
    }
}
