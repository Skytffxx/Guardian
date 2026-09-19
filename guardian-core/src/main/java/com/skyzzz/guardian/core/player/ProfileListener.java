package com.skyzzz.guardian.core.player;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.GuardianConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import com.skyzzz.guardian.api.data.DamageData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/**
 * Populates each player's {@link GuardianProfile} with the per-tick gameplay state
 * that checks read via {@code profile.attribute(...)} — potions, block materials,
 * enchantments, fall distance, and the transient teleport/glide/vehicle flags.
 *
 * Everything here runs on the main/region thread and writes to the profile's
 * attribute map. Checks read those attributes on the same thread, so no locking is
 * needed beyond the map's own concurrency.
 */
public final class ProfileListener implements Listener {

    /** How many ticks the teleport flag stays set after a teleport. */
    private static final int TELEPORT_GRACE_TICKS = 5;

    private final GuardianPlugin plugin;
    private final GuardianProfileManager profiles;
    private final GuardianConfig config;

    public ProfileListener(GuardianPlugin plugin, GuardianProfileManager profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.config = plugin.guardianConfig();
    }

    // ---- lifecycle -------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.setAttribute("last-item-switch-tick", profile.tick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null) {
            return;
        }
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        if (hand.getType() == Material.FIREWORK_ROCKET) {
            profile.setAttribute("firework-boost-tick", profile.tick());
        }
        // Consumable tracking for FastUseCheck: start timestamp here, duration
        // resolved in onItemConsume.
        if (hand.getType().isEdible() || hand.getType().name().endsWith("_POTION")
                || hand.getType() == Material.MILK_BUCKET
                || hand.getType() == Material.HONEY_BOTTLE) {
            profile.setAttribute("use-start-nanos", System.nanoTime());
        }
    }

    // ---- world interaction feeds (scaffold/fastplace/fastbreak/nuker/...) -----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        org.bukkit.Location eye = event.getPlayer().getEyeLocation();
        org.bukkit.block.Block block = event.getBlockPlaced();
        double dx = block.getX() + 0.5D - eye.getX();
        double dy = block.getY() + 0.5D - eye.getY();
        double dz = block.getZ() + 0.5D - eye.getZ();
        double eyeDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean lineOfSight;
        try {
            lineOfSight = event.getPlayer().hasLineOfSight(
                    block.getLocation().add(0.5D, 0.5D, 0.5D));
        } catch (Throwable ignored) {
            lineOfSight = eyeDistance <= 6.0D;
        }
        com.skyzzz.guardian.api.data.BlockPlaceData data =
                new com.skyzzz.guardian.api.data.BlockPlaceData(
                        block.getX(), block.getY(), block.getZ(), block.getType().name(),
                        eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                        faceId(event.getBlockAgainst(), block),
                        eyeDistance, lineOfSight, System.nanoTime());
        plugin.checkRegistry().dispatchBlockPlace(profile, data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        org.bukkit.block.Block block = event.getBlock();
        org.bukkit.Location eye = event.getPlayer().getEyeLocation();
        double dx = block.getX() + 0.5D - eye.getX();
        double dy = block.getY() + 0.5D - eye.getY();
        double dz = block.getZ() + 0.5D - eye.getZ();
        double eyeDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean lineOfSight;
        try {
            lineOfSight = event.getPlayer().hasLineOfSight(
                    block.getLocation().add(0.5D, 0.5D, 0.5D));
        } catch (Throwable ignored) {
            lineOfSight = eyeDistance <= 6.0D;
        }
        boolean instantBreak = isInstantBreak(block.getType());
        profile.setAttribute("last-break-eye-distance", eyeDistance);
        profile.setAttribute("last-break-line-of-sight",
                lineOfSight ? Boolean.TRUE : Boolean.FALSE);
        com.skyzzz.guardian.api.data.BlockBreakData data =
                new com.skyzzz.guardian.api.data.BlockBreakData(
                        block.getX(), block.getY(), block.getZ(),
                        block.getType().name(), instantBreak, System.nanoTime());
        plugin.checkRegistry().dispatchBlockBreak(profile, data);
    }

    private static int faceId(org.bukkit.block.Block against, org.bukkit.block.Block placed) {
        if (against == null || placed == null) {
            return -1;
        }
        int dx = placed.getX() - against.getX();
        int dy = placed.getY() - against.getY();
        int dz = placed.getZ() - against.getZ();
        if (dx == 1) {
            return 0;
        }
        if (dx == -1) {
            return 1;
        }
        if (dy == 1) {
            return 2;
        }
        if (dy == -1) {
            return 3;
        }
        if (dz == 1) {
            return 4;
        }
        if (dz == -1) {
            return 5;
        }
        return -1;
    }

    private static boolean isInstantBreak(Material material) {
        if (material.getHardness() == 0.0F) {
            return true;
        }
        return switch (material.name()) {
            case "SHORT_GRASS", "TALL_GRASS", "FERN", "TORCH", "SNOW",
                    "VINE", "LILY_PAD", "SCAFFOLDING", "SEAGRASS", "COBWEB",
                    "RAIL", "LEVER", "REDSTONE_WIRE" -> true;
            default -> material.name().endsWith("_BUTTON")
                    || material.name().endsWith("_PRESSURE_PLATE")
                    || material.name().endsWith("_CARPET")
                    || material.name().endsWith("_SAPLING");
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.setAttribute("client-sneaking", event.isSneaking() ? Boolean.TRUE : null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(org.bukkit.event.player.PlayerToggleSprintEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.setAttribute("server-sprinting", event.isSprinting() ? Boolean.TRUE : null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GuardianProfile profile = profiles.getOrCreate(player);

        // Permission-based exemptions, resolved once at join.
        applyPermissionExemptions(player, profile);

        // Gamemode exemptions.
        applyGamemode(profile, player.getGameMode().name());

        plugin.checkRegistry().dispatchJoin(profile);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GuardianProfile profile = (GuardianProfile) profiles.get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        plugin.checkRegistry().dispatchQuit(profile);

        if (config.getBoolean("violations.reset-on-quit", true)) {
            profile.reset();
        }
        profiles.remove(player.getUniqueId());
        plugin.floodgateHook().invalidate(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim == attacker) {
            return;
        }
        // Only melee swings can crit — skip projectiles and AoE damage sources.
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }

        GuardianProfile profile = (GuardianProfile) profiles.get(attacker.getUniqueId());
        if (profile == null) {
            return;
        }

        boolean critical = detectCritical(attacker, victim, event.getDamage());

        DamageData data = new DamageData(
                   victim.getType().name(),
                   event.getFinalDamage(),
                   !attacker.isOnGround(),
                   attacker.getFallDistance(),
                   attacker.isClimbing(),
                   attacker.isInWater(),
                   attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS),
                   attacker.isInsideVehicle(),
                   attacker.isSprinting(),
                   critical,
                   System.nanoTime());

           plugin.checkRegistry().dispatchDamage(profile, data);
    }

    /**
     * Vanilla crit detection. True when the server applied the 1.5x multiplier.
     *
     * The check is: damage is a multiple of 1.5 of base. We approximate base as the
     * weapon's attack damage attribute. If the ratio is within tolerance of 1.5, the
     * hit was a crit.
     */
    private boolean detectCritical(Player attacker, Entity victim, double damage) {
        // No crit possible if server already knows these conditions failed.
        if (attacker.isOnGround()
                || attacker.isClimbing()
                || attacker.isInWater()
                || attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                || attacker.isInsideVehicle()) {
            return false;
        }

        double base = attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null
                ? attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue()
                : 1.0D;

        if (base <= 0.0D) {
            return false;
        }

        double ratio = damage / base;
     return Math.abs(ratio - 1.5D) < 0.15D;
    }

    // ---- gamemode / state toggles ---------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        applyGamemode(profile, event.getNewGameMode().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GuardianProfile profile = (GuardianProfile) profiles.get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        profile.setAttribute("elytra", event.isGliding() ? Boolean.TRUE : null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        Object startAttr = profile.attribute("use-start-nanos");
        if (startAttr instanceof Long startNanos) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (durationMs >= 0L && durationMs < 30_000L) {
                profile.setAttribute("last-use-duration-ms", (double) durationMs);
                // Synthetic packet name in PacketEvents UPPER_SNAKE style so the
                // format-tolerant matcher in FastUseCheck picks it up on any version.
                plugin.checkRegistry().dispatchPacketReceive(profile,
                        new com.skyzzz.guardian.api.data.PacketData(
                                event.getItem(), Object.class, "USE_ITEM",
                                com.skyzzz.guardian.api.PacketDirection.INBOUND,
                                System.nanoTime()));
            }
        }
        profile.setAttribute("use-start-nanos", null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        profile.setAttribute("teleport", Boolean.TRUE);

        // One-shot delayed clear: no repeating-task leak, Folia-safe.
        plugin.schedulers().runSyncDelayed(() -> {
            Object stillTeleport = profile.attribute("teleport");
            if (Boolean.TRUE.equals(stillTeleport)) {
                profile.setAttribute("teleport", null);
            }
        }, TELEPORT_GRACE_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getEntity().getUniqueId());
        if (profile == null) {
            return;
        }
        // AutoRespawnCheck exposes onDeath(UUID, long) for exactly this wiring.
        var check = plugin.checkRegistry().get("autorespawn");
        if (check.isPresent()
                && check.get() instanceof com.skyzzz.guardian.checks.player.AutoRespawnCheck autoRespawn) {
            autoRespawn.onDeath(event.getEntity().getUniqueId(), System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.setAttribute("teleport", Boolean.TRUE);
        }
    }

    // ---- per-move state --------------------------------------------------

    // Throttle: full attribute scans run at most this often per player.
    // PlayerMoveEvent fires several times per tick; the checks only need freshness
    // at the server tick rate.
    private static final long ATTRIBUTE_SCAN_INTERVAL_NANOS = 25_000_000L;

    /**
     * The highest-frequency handler in the plugin. Paper fires this multiple times
     * per tick, so we keep it cheap: only block-material lookups and potion reads
     * that checks actually consume.
     *
     * Realistically, once {@link com.skyzzz.guardian.core.packet.GuardianPacketListener}
     * starts driving move events off packet data, this listener becomes a supplement
     * for state that only Bukkit knows (potions, enchantments, exact block under
     * the player's feet). It does not need to fire the checks itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }

        Player player = event.getPlayer();

        // Throttle full scans: PlayerMoveEvent fires multiple times per tick.
        long now = System.nanoTime();
        Object lastScanAttr = profile.attribute("attribute-scan-nanos");
        long lastScan = lastScanAttr instanceof Long value ? value : 0L;
        boolean fullScan = now - lastScan >= ATTRIBUTE_SCAN_INTERVAL_NANOS
                || event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (!fullScan) {
            return;
        }
        profile.setAttribute("attribute-scan-nanos", now);

        updateBlockAttributes(profile, player);
        updatePotionAttributes(profile, player);
        updateEnchantmentAttributes(profile, player);

        profile.setAttribute("fall-distance", player.getFallDistance());
        profile.setAttribute("server-on-ground", player.isOnGround());
        profile.setAttribute("vehicle", player.isInsideVehicle() ? Boolean.TRUE : null);

        // --- additional feeds for vehicle / phase / regen / fastbreak ---
        // Skip the vehicle Location lookup when the player is not riding: getLocation()
        // on a vehicle entity is a world access and this handler is the hottest in the plugin.
        if (player.isInsideVehicle()) {
            org.bukkit.entity.Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                org.bukkit.Location vehicleLoc = vehicle.getLocation();
                profile.setAttribute("vehicle", Boolean.TRUE);
                profile.setAttribute("vehicle-x", vehicleLoc.getX());
                profile.setAttribute("vehicle-y", vehicleLoc.getY());
                profile.setAttribute("vehicle-z", vehicleLoc.getZ());
            } else {
                profile.setAttribute("vehicle", null);
                profile.setAttribute("vehicle-x", null);
                profile.setAttribute("vehicle-y", null);
                profile.setAttribute("vehicle-z", null);
            }
        } else {
            profile.setAttribute("vehicle", null);
            profile.setAttribute("vehicle-x", null);
            profile.setAttribute("vehicle-y", null);
            profile.setAttribute("vehicle-z", null);
        }

        profile.setAttribute("health", player.getHealth());
        profile.setAttribute("server-sneaking", player.isSneaking() ? Boolean.TRUE : null);
        profile.setAttribute("server-sprinting", player.isSprinting() ? Boolean.TRUE : null);

        // phase detection — is the player's bounding volume intersecting solids?
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.block.Block feetBlock = loc.getBlock();
        boolean feetInSolid = !feetBlock.isPassable()
                && feetBlock.getType().isSolid();
        org.bukkit.block.Block headBlock = loc.clone().add(0, 1.4D, 0).getBlock();
        boolean headInSolid = !headBlock.isPassable()
                && headBlock.getType().isSolid();
        profile.setAttribute("feet-in-solid", feetInSolid ? Boolean.TRUE : null);
        profile.setAttribute("head-in-solid", headInSolid ? Boolean.TRUE : null);

        // fastbreak — record the block being broken and when
        org.bukkit.block.Block belowFeet = feetBlock.getRelative(0, -1, 0);
        profile.setAttribute("block-below-type", belowFeet.getType().name());
        profile.setAttribute("block-below-hardness", belowFeet.getType().getHardness());
        profile.setAttribute("block-at-feet-type", feetBlock.getType().name());
        profile.setAttribute("block-at-feet-hardness", feetBlock.getType().getHardness());

        org.bukkit.inventory.ItemStack tool = player.getInventory().getItemInMainHand();
        int digSpeedLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
        profile.setAttribute("tool-efficiency", digSpeedLevel);
        profile.setAttribute("tool-type", tool.getType().name());
    }
    // ---- attribute feeds -------------------------------------------------

    private void updateBlockAttributes(GuardianProfile profile, Player player) {
        org.bukkit.Location location = player.getLocation();
        Block feet = location.getBlock();
        Block below = feet.getRelative(0, -1, 0);

        Material feetType = feet.getType();
        Material belowType = below.getType();

        profile.setAttribute("in-water",
                feetType == Material.WATER ? Boolean.TRUE : null);
        profile.setAttribute("in-liquid",
                isLiquid(feetType) ? Boolean.TRUE : null);

        boolean climbable = isClimbable(feetType);
        profile.setAttribute("on-climbable", climbable ? Boolean.TRUE : null);
        profile.setAttribute("on-ladder", climbable ? Boolean.TRUE : null);

        profile.setAttribute("on-ice", isIce(belowType) ? Boolean.TRUE : null);
        profile.setAttribute("on-slime", belowType == Material.SLIME_BLOCK ? Boolean.TRUE : null);
        profile.setAttribute("soul-speed",
                belowType == Material.SOUL_SAND || belowType == Material.SOUL_SOIL
                        ? Boolean.TRUE : null);
    }

    private void updatePotionAttributes(GuardianProfile profile, Player player) {
        setAmplifier(profile, player, PotionEffectType.SPEED, "speed-amplifier");
        setAmplifier(profile, player, PotionEffectType.JUMP_BOOST, "jump-boost-amplifier");

        profile.setAttribute("levitation-effect",
                player.hasPotionEffect(PotionEffectType.LEVITATION) ? Boolean.TRUE : null);
        profile.setAttribute("slow-falling",
                player.hasPotionEffect(PotionEffectType.SLOW_FALLING) ? Boolean.TRUE : null);
        profile.setAttribute("levitating",
                player.hasPotionEffect(PotionEffectType.LEVITATION) ? Boolean.TRUE : null);
    }

    private void updateEnchantmentAttributes(GuardianProfile profile, Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null) {
            profile.setAttribute("depth-strider", null);
            return;
        }
        boolean depthStrider = boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER) > 0;
        profile.setAttribute("depth-strider", depthStrider ? Boolean.TRUE : null);
    }

    // ---- helpers ---------------------------------------------------------

    private void applyGamemode(GuardianProfile profile, String gameModeName) {
        boolean creative = "CREATIVE".equalsIgnoreCase(gameModeName);
        boolean spectator = "SPECTATOR".equalsIgnoreCase(gameModeName);

        profile.setAttribute("creative", creative ? Boolean.TRUE : null);
        profile.setAttribute("spectator", spectator ? Boolean.TRUE : null);

        profile.setExempt("combat", creative || spectator);
        profile.setExempt("movement", creative || spectator);
    }

    private void applyPermissionExemptions(Player player, GuardianProfile profile) {
        if (player.hasPermission("guardian.exempt")) {
            profile.setExempt("all", true);
            return;
        }
        profile.setExempt("combat", player.hasPermission("guardian.exempt.combat"));
        profile.setExempt("movement", player.hasPermission("guardian.exempt.movement"));
        profile.setExempt("world", player.hasPermission("guardian.exempt.world"));
        profile.setExempt("player", player.hasPermission("guardian.exempt.player"));
        profile.setExempt("packet", player.hasPermission("guardian.exempt.packet"));
    }

    private void setAmplifier(GuardianProfile profile, Player player,
                              PotionEffectType type, String attributeKey) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect == null) {
            profile.setAttribute(attributeKey, null);
        } else {
            profile.setAttribute(attributeKey, effect.getAmplifier());
        }
    }

    private boolean isLiquid(Material material) {
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.BUBBLE_COLUMN;
    }

    private boolean isClimbable(Material material) {
        return material == Material.LADDER
                || material == Material.VINE
                || material == Material.SCAFFOLDING
                || material == Material.TWISTING_VINES
                || material == Material.TWISTING_VINES_PLANT
                || material == Material.WEEPING_VINES
                || material == Material.WEEPING_VINES_PLANT
                || material == Material.CAVE_VINES
                || material == Material.CAVE_VINES_PLANT;
    }

    private boolean isIce(Material material) {
        return material == Material.ICE
                || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE
                || material == Material.FROSTED_ICE;
    }
}