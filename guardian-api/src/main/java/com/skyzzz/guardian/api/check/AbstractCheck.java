package com.skyzzz.guardian.api.check;

import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Base class giving checks config access, weighted flagging and the Bedrock
 * threshold-scale shortcut. Checks extend this; nothing else should.
 */
public abstract class AbstractCheck implements Check {

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

    // ---- config shortcuts ------------------------------------------------

    protected double d(String key, double def) {
        return settings == null ? def : settings.getDouble(key, def);
    }

    protected int i(String key, int def) {
        return settings == null ? def : settings.getInt(key, def);
    }

    protected boolean b(String key, boolean def) {
        return settings == null ? false : settings.getBoolean(key, def);
    }

    protected String s(String key, String def) {
        return settings == null ? def : settings.getString(key, def);
    }

    /** Threshold scaled by the player's Bedrock multiplier (1.0 for Java). */
    protected double scaled(PlayerProfile profile, String key, double base) {
        return settings == null ? base : settings.scaled(key, base, profile.thresholdScale());
    }

    // ---- flagging --------------------------------------------------------

    /** Flags with the check's configured {@code vl-weight} (default 1.0). */
    protected final void flag(PlayerProfile profile, String debug, Object... args) {
        profile.flag(this, d("vl-weight", 1.0D), debug, args);
    }

    /** Flags with {@code vl-weight * weight}. Use for severity-scaled flags. */
    protected final void flag(PlayerProfile profile, double weight, String debug, Object... args) {
        profile.flag(this, d("vl-weight", 1.0D) * weight, debug, args);
    }

    /** Awards decay credit for demonstrably clean behaviour. */
    protected final void reward(PlayerProfile profile, double amount) {
        profile.reward(this, amount);
    }
}