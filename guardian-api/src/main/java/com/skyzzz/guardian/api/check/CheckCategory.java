package com.skyzzz.guardian.api.check;

public enum CheckCategory {
    COMBAT("combat"),
    MOVEMENT("movement"),
    WORLD("world"),
    PLAYER("player"),
    PACKET("packet");

    private final String key;

    CheckCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static CheckCategory fromKey(String key) {
        for (CheckCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown check category: " + key);
    }
}