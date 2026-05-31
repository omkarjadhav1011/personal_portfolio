package com.portfolio.common;

import java.time.Instant;
import java.util.UUID;

/**
 * Implemented by entities that carry a {@code sort_order} and participate in the admin
 * reorder endpoints. Lets the reorder logic be written once, generically.
 */
public interface Sortable {
    UUID getId();

    int getSortOrder();

    void setSortOrder(int sortOrder);

    Instant getCreatedAt();
}
