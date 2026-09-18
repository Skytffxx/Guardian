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
}