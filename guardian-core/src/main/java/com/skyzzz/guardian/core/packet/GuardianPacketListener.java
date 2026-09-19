package com.skyzzz.guardian.core.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.skyzzz.guardian.api.PacketDirection;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PositionSample;
import com.skyzzz.guardian.api.util.MathUtil;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.check.CheckRegistryImpl;
import com.skyzzz.guardian.core.player.GuardianProfile;
import com.skyzzz.guardian.core.player.GuardianProfileManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.UUID;

/**
 * The hot path. Everything here must be allocation-light: this runs once per packet
 * per player, so at 200 players a single unnecessary object shows up in the profiler.
 *
 * Packet parsing and the math-heavy checks run off the main thread. Anything that
 * needs world/block state is hopped back to the main thread via the scheduler.
 */
public final class GuardianPacketListener implements PacketListener {

    private final GuardianPlugin plugin;
    private final GuardianProfileManager profiles;
    private final CheckRegistryImpl registry;

    public GuardianPacketListener(GuardianPlugin plugin, GuardianProfileManager profiles,
                                  CheckRegistryImpl registry) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.registry = registry;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        GuardianProfile profile = (GuardianProfile) profiles.get(player.getUniqueId());
        if (profile == null) {
            return;
        }

        long now = System.nanoTime();

        PacketData data = new PacketData(event.getPacketType(), event.getPacketType().getClass(),
                event.getPacketType().getName(), PacketDirection.INBOUND, now);
        registry.dispatchPacketReceive(profile, data);

        // --- inventory click window: capture slot for InventoryCheck ---
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            try {
                WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
                profile.setAttribute("last-click-slot", click.getSlot());
                profile.setAttribute("last-click-window-id", click.getWindowId());
            } catch (Throwable ignored) {
                // Protocol variation across versions — fall through rather than crash dispatch.
            }
        }

        // --- keep-alive response ---
        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            WrapperPlayClientKeepAlive ka = new WrapperPlayClientKeepAlive(event);
            profile.setAttribute("keepalive-response-id", ka.getId());
            profile.setAttribute("keepalive-response-nanos", System.nanoTime());
            return;
        }

        // --- movement packets: fully async, no world access needed ---
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            handleMovement(event, profile, now);
            return;
        }

        // --- attack packets: need world state for hitboxes, so hop to main ---
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            handleInteract(event, player, profile, now);
        }
    }

    private void handleMovement(PacketReceiveEvent event, GuardianProfile profile, long now) {
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        PositionSample last = profile.positions().latest();

        double x = flying.hasPositionChanged() ? flying.getLocation().getX() : (last == null ? 0 : last.x());
        double y = flying.hasPositionChanged() ? flying.getLocation().getY() : (last == null ? 0 : last.y());
        double z = flying.hasPositionChanged() ? flying.getLocation().getZ() : (last == null ? 0 : last.z());
        float yaw = flying.hasRotationChanged() ? flying.getLocation().getYaw() : (last == null ? 0 : last.yaw());
        float pitch = flying.hasRotationChanged() ? flying.getLocation().getPitch() : (last == null ? 0 : last.pitch());
        boolean onGround = flying.isOnGround();

        double lastX = last == null ? x : last.x();
        double lastY = last == null ? y : last.y();
        double lastZ = last == null ? z : last.z();
        float lastYaw = last == null ? yaw : last.yaw();
        float lastPitch = last == null ? pitch : last.pitch();
        boolean lastOnGround = last != null && last.onGround();

        MoveData move = new MoveData(x, y, z, lastX, lastY, lastZ, yaw, pitch, lastYaw, lastPitch,
                onGround, lastOnGround, flying.hasPositionChanged(), flying.hasRotationChanged(),
                1.62D, now);

        profile.positions().add(new PositionSample(x, y, z, yaw, pitch, onGround, now));
        registry.dispatchMove(profile, move);
    }

    private void handleInteract(PacketReceiveEvent event, Player player,
                                GuardianProfile profile, long now) {
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        int targetId = interact.getEntityId();
        var location = player.getEyeLocation();
        double eyeX = location.getX();
        double eyeY = location.getY();
        double eyeZ = location.getZ();

        // World access → main thread.
        plugin.schedulers().runSync(() -> {
            Entity target = null;
            for (Entity candidate : player.getWorld().getNearbyEntities(location, 8.0D, 8.0D, 8.0D)) {
                if (candidate.getEntityId() == targetId) {
                    target = candidate;
                    break;
                }
            }

            if (target == null) {
                registry.dispatchAttack(profile, new AttackData(targetId, "unknown",
                        eyeX, eyeY, eyeZ, player.getLocation().getYaw(),
                        player.getLocation().getPitch(), -1.0D, false, false, true, now));
                return;
            }

            BoundingBox box = target.getBoundingBox();
            double distance = MathUtil.distanceToBox(eyeX, eyeY, eyeZ,
                    box.getMinX(), box.getMinY(), box.getMinZ(),
                    box.getMaxX(), box.getMaxY(), box.getMaxZ());

            boolean lineOfSight = player.hasLineOfSight(target);

            registry.dispatchAttack(profile, new AttackData(
                    targetId, target.getType().name(),
                    eyeX, eyeY, eyeZ,
                    player.getLocation().getYaw(), player.getLocation().getPitch(),
                    distance, lineOfSight, true, true, now));
        });
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID uuid = event.getUser().getUUID();
        if (uuid == null) {
            return;
        }
        GuardianProfile profile = (GuardianProfile) profiles.get(uuid);
        if (profile == null) {
            return;
        }

        PacketData data = new PacketData(event.getPacketType(), event.getPacketType().getClass(),
                event.getPacketType().getName(), PacketDirection.OUTBOUND, System.nanoTime());
        registry.dispatchPacketSend(profile, data);

        // --- outbound velocity ---
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity velocity = new WrapperPlayServerEntityVelocity(event);
            Integer entityId = event.getUser().getEntityId();
            if (entityId != null && velocity.getEntityId() == entityId) {
                profile.setAttribute("velocity-x", (double) velocity.getVelocity().getX());
                profile.setAttribute("velocity-y", (double) velocity.getVelocity().getY());
                profile.setAttribute("velocity-z", (double) velocity.getVelocity().getZ());
                profile.setAttribute("velocity-tick", profile.tick());
                profile.setAttribute("velocity-x-applied", null);
                profile.setAttribute("velocity-z-applied", null);
            }
        }

        // --- outbound keep-alive ---
        if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            WrapperPlayServerKeepAlive ka = new WrapperPlayServerKeepAlive(event);
            profile.setAttribute("keepalive-sent-id", ka.getId());
            profile.setAttribute("keepalive-sent-nanos", System.nanoTime());
        }
    }
}