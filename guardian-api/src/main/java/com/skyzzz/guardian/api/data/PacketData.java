package com.skyzzz.guardian.api.data;

import com.skyzzz.guardian.api.PacketDirection;

/**
 * Thin envelope around a raw packet. {@code packet} stays {@link Object} so the API
 * module never depends on PacketEvents; packet-level checks in guardian-checks cast it.
 */
public record PacketData(
        Object packet,
        Class<?> packetType,
        String packetName,
        PacketDirection direction,
        long timestampNanos
) {

    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) {
        return type.isInstance(packet) ? (T) packet : null;
    }

    /**
     * Format-tolerant packet-name match. PacketEvents names vary by version
     * ({@code KEEP_ALIVE}, {@code keep_alive}, {@code KeepAlive}), so both sides
     * are lowercased with non-alphanumerics stripped before comparing.
     */
    public boolean matchesName(String keyword) {
        if (packetName == null || keyword == null) {
            return false;
        }
        return normalize(packetName).contains(normalize(keyword));
    }

    private static String normalize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int idx = 0; idx < value.length(); idx++) {
            char c = value.charAt(idx);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}