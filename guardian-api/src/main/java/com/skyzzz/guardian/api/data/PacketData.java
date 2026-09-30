package com.skyzzz.guardian.api.data;

import com.skyzzz.guardian.api.PacketDirection;

/**
 * Thin envelope around a raw packet. The packet stays typed as {@link Object} so this
 * module keeps no PacketEvents dependency; packet-level checks do the casting.
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
     * PacketEvents names the same packet {@code KEEP_ALIVE}, {@code keep_alive} or
     * {@code KeepAlive} depending on version, so both sides are lowercased with
     * non-alphanumerics stripped before comparing.
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