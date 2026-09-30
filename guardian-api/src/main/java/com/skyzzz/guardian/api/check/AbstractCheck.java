package com.skyzzz.guardian.api.check;

import com.skyzzz.guardian.api.player.PlayerProfile;

/** Base for all checks. */
public abstract class AbstractCheck implements Check {

    private static final String VL_WEIGHT = "vl-weight";
    private static final double DEFAULT_VL_WEIGHT = 1.0D;

    private final String name;
    private final CheckCategory category;

    private volatile CheckSettings settings;
    private volatile boolean enabled = true;

    protected AbstractCheck(String name, CheckCategory category) {
        this.name = name;
        this.category = category;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final CheckCategory category() {
        return category;
    }

    @Override
    public final CheckSettings settings() {
        return settings;
    }

    @Override
    public void bind(CheckSettings settings) {
        this.settings = settings;
        this.enabled = settings.enabled();
        onReload();
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected double d(String key, double def) {
        return settings == null ? def : settings.getDouble(key, def);
    }

    protected int i(String key, int def) {
        return settings == null ? def : settings.getInt(key, def);
    }

    protected boolean b(String key, boolean def) {
        return settings == null ? def : settings.getBoolean(key, def);
    }

    protected String s(String key, String def) {
        return settings == null ? def : settings.getString(key, def);
    }

    /** Widened by the player's platform multiplier; no-op for Java. */
    protected double scaled(PlayerProfile profile, String key, double base) {
        return settings == null ? base : settings.scaled(key, base, profile.thresholdScale());
    }

    protected final void flag(PlayerProfile profile, String debug, Object... args) {
        profile.flag(this, d(VL_WEIGHT, DEFAULT_VL_WEIGHT), debug, args);
    }

    /** For severity-scaled flags; multiplies the configured vl-weight. */
    protected final void flag(PlayerProfile profile, double weight, String debug, Object... args) {
        profile.flag(this, d(VL_WEIGHT, DEFAULT_VL_WEIGHT) * weight, debug, args);
    }

    protected final void reward(PlayerProfile profile, double amount) {
        profile.reward(this, amount);
    }
}