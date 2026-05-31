package com.portfolio.admin;

import java.util.List;

/**
 * Payload for {@code /api/admin/reorder}. Two modes:
 * <ul>
 *   <li>bulk: {@code items} present — set each entity's order to its {@code position};</li>
 *   <li>adjacent swap: {@code id} + {@code direction} ("up"/"down") — swap with neighbor.</li>
 * </ul>
 * {@code type} is one of {@code stack | projects | experience}.
 */
public record ReorderRequest(
        String type,
        List<ReorderItem> items,
        String id,
        String direction
) {
    public record ReorderItem(String id, int position) {
    }
}
