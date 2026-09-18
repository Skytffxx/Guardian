package com.skyzzz.guardian.core.player;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.GuardianConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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
    public void onTeleport(PlayerTeleportEvent event) {
        GuardianProfile profile = (GuardianProfile) profiles.get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        profile.setAttribute("teleport", Boolean.TRUE);

        // Schedule the flag to clear after the grace window. Uses the plugin's
        // Folia-aware scheduler so this works on region-threaded servers too.
        long clearAtTick = profile.tick() + TELEPORT_GRACE_TICKS;
        plugin.schedulers().runSyncRepeating(new Runnable() {
            @Override
            public void run() {
                if (profile.tick() >= clearAtTick) {
                    profile.setAttribute("teleport", null);
                }
            }
        }, TELEPORT_GRACE_TICKS, TELEPORT_GRACE_TICKS);
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

        // Cheap early-out: if nothing changed but rotation, most attributes are stale.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            updatePotionAttributes(profile, player);
            return;
        }

        updateBlockAttributes(profile, player);
        updatePotionAttributes(profile, player);
        updateEnchantmentAttributes(profile, player);

        profile.setAttribute("fall-distance", player.getFallDistance());
        profile.setAttribute("server-on-ground", player.isOnGround());
        profile.setAttribute("vehicle", player.isInsideVehicle() ? Boolean.TRUE : null);
    }

    // ---- attribute feeds -------------------------------------------------

    private void updateBlockAttributes(GuardianProfile profile, Player player) {
        Block feet = player.getLocation().getBlock();
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