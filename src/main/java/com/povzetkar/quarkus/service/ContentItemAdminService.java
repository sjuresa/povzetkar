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
    public boolean updateStatus(Long contentItemId, ProcessingStatus newStatus) {
        Objects.requireNonNull(contentItemId, "Content Item ID cannot be null");
        Objects.requireNonNull(newStatus, "New Status cannot be null");

        Optional<ContentItem> itemOptional = ContentItem.findByIdOptional(contentItemId);

        if (itemOptional.isEmpty()) {
            logger.warn(ACTION_KEY, UPDATE_STATUS_ACTION, STATUS_KEY, "not_found", CONTENT_ITEM_ID_KEY, contentItemId, MESSAGE_KEY, "Content item not found.");
            return false;
        }

        ContentItem item = itemOptional.get();
        ProcessingStatus oldStatus = item.processingStatus;

        // Basic validation: prevent setting to the same status or invalid transitions
        if (oldStatus == newStatus) {
            logger.info(ACTION_KEY, UPDATE_STATUS_ACTION, STATUS_KEY, "no_change", CONTENT_ITEM_ID_KEY, contentItemId, MESSAGE_KEY, "Status is already " + newStatus);
            return true; // No change needed
        }

        item.processingStatus = newStatus;
        Panache.getEntityManager().merge(item);

        logger.info(ACTION_KEY, UPDATE_STATUS_ACTION, STATUS_KEY, "success", CONTENT_ITEM_ID_KEY, contentItemId, "old_status", oldStatus, "new_status", newStatus, MESSAGE_KEY, "Successfully updated content item status.");
        return true;
    }
}
