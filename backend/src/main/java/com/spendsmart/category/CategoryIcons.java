package com.spendsmart.category;

import java.util.List;
import java.util.Set;

/** Icon keys understood by the frontend icon map. Anything else is rejected rather than rendered. */
public final class CategoryIcons {

    public static final List<String> ALL = List.of(
            "utensils", "shopping-cart", "bus", "car", "fuel", "graduation-cap", "shopping-bag", "home", "zap",
            "wifi", "smartphone", "heart-pulse", "film", "music", "gamepad", "repeat", "plane", "briefcase", "laptop",
            "gift", "piggy-bank", "trending-up", "baby", "paw-print", "shirt", "dumbbell", "coffee", "book",
            "wrench", "receipt", "landmark", "hand-coins", "circle-dot");

    private static final Set<String> ALLOWED = Set.copyOf(ALL);

    private CategoryIcons() {
    }

    public static boolean isAllowed(String icon) {
        return icon != null && ALLOWED.contains(icon);
    }
}
