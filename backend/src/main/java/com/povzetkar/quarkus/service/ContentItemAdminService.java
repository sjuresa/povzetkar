package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class ContentItemAdminService {

    private static final StructuredLogger logger = StructuredLogger.getLogger(ContentItemAdminService.class);

    private static final String ACTION_KEY = "action";
    private static final String UPDATE_STATUS_ACTION = "update_status";
    private static final String STATUS_KEY = "status";
    private static final String CONTENT_ITEM_ID_KEY = "content_item_id";
    private static final String MESSAGE_KEY = "message";

    @Transactional
    public Optional<ContentItem> updateStatus(Long contentItemId, ProcessingStatus newStatus) {
        return updateStatus(contentItemId, newStatus, "SYSTEM");
    }

    @Transactional
    public Optional<ContentItem> updateStatus(Long contentItemId, ProcessingStatus newStatus, String context) {
        Objects.requireNonNull(contentItemId, "Content Item ID cannot be null");
        return ContentItem.<ContentItem>findByIdOptional(contentItemId)
                .map(item -> updateStatus(item, newStatus, context));
    }

    @Transactional
    public ContentItem updateStatus(ContentItem item, ProcessingStatus newStatus, String context) {
        Objects.requireNonNull(item, "Content Item cannot be null");
        Objects.requireNonNull(newStatus, "New Status cannot be null");

        ProcessingStatus oldStatus = item.processingStatus;

        // Basic validation: prevent setting to the same status
        if (oldStatus == newStatus) {
            logger.info(ACTION_KEY, UPDATE_STATUS_ACTION, STATUS_KEY, "no_change", CONTENT_ITEM_ID_KEY, item.id, MESSAGE_KEY, "Status is already " + newStatus);
            return item; // No change needed
        }

        item.processingStatus = newStatus;
        ContentItem mergedItem = Panache.getEntityManager().merge(item);

        String logPrefix = "ADMIN".equals(context) ? "[ADMIN_OVERRIDE] " : "";
        logger.info(ACTION_KEY, UPDATE_STATUS_ACTION, STATUS_KEY, "success", CONTENT_ITEM_ID_KEY, mergedItem.id, "old_status", oldStatus, "new_status", newStatus, "context", context, MESSAGE_KEY, logPrefix + "Successfully updated content item status.");
        return mergedItem;
    }
}
