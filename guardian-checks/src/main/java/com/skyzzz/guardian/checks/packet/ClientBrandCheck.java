package com.skyzzz.guardian.checks.packet;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client brand spoofing: the brand string changes mid-session, is missing entirely,
 * or matches a known cheat-client signature. Missing brand alone is not a flag —
 * only a mid-session change or a signature match is.
 */
public final class ClientBrandCheck extends AbstractCheck {

    private final Map<UUID, String> brands = new ConcurrentHashMap<>();

    public ClientBrandCheck() {
        super("clientbrand", CheckCategory.PACKET);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (!packet.packetName().contains("ClientSettings")) {
            return;
        }
        Object brandAttr = profile.attribute("client-brand");
        if (!(brandAttr instanceof String brand) || brand.isBlank()) {
            return;
        }

        String previous = brands.put(profile.uuid(), brand);
        if (previous != null && !previous.equals(brand)) {
            flag(profile, 1.0D, "client brand changed '%s' -> '%s'", previous, brand);
            return;
        }

        for (String signature : settings().getStringList("blocked-signatures")) {
            if (!signature.isBlank() && brand.toLowerCase().contains(signature.toLowerCase())) {
                flag(profile, 3.0D, "blocked client brand '%s' (matched '%s')", brand, signature);
                return;
            }
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        brands.remove(profile.uuid());
    }
}