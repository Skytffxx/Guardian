# Guardian — Changelog

All notable changes to the plugin. Newest first.

---

## v1.0.1 — 2026-09-19

### Fixed
- **FastUse detection now works** — eat/drink speed is actually measured again,
  so finishing food or potions faster than vanilla allows is flagged.
- **World checks now receive events** — Scaffold, FastPlace, FastBreak, Nuker,
  AutoTool, BlockReach and the Xray heuristic were silently getting no data and
  could never trigger. They are all live now.
- **Nuker no longer flags instant-break blocks** — grass, flowers, torches,
  snow layers and similar zero-hardness blocks are correctly ignored.
- **BlockReach rewritten** — now covers both placing and breaking, evaluates
  over a rolling window instead of flagging on a single interaction, and
  forgives clean interactions. Fewer false positives.
- **Packet detection works across versions** — KeepAlive, click-window,
  client-settings and related detections no longer miss packets because of
  naming differences between server versions.
- **FastBreak false positives after teleports/pauses** — break intervals split
  by a teleport, world change or long pause no longer count as one fast break
  (new `ignore-above-ms` setting, default 10 seconds).
- **Xray review-only mode respected** — the Xray heuristic can no longer
  auto-punish; it only raises review-level alerts as intended.
- **Teleport handling no longer leaks scheduled tasks** — repeated teleports
  used to pile up background tasks; grace flags are now cleared with a single
  one-shot task.

### Bedrock support
- **Geyser servers without Floodgate are detected** — Bedrock players get their
  leniency thresholds even when only Geyser-Spigot is installed.
- **Faster Bedrock detection** — platform lookups are cached per player instead
  of hitting the API every time.
- **Reload picks up Bedrock installs** — `/guardian reload` re-checks online
  players, so installing Geyser/Floodgate no longer requires everyone to rejoin.
- **Velocity is fairer on Bedrock** — new `bedrock-knockback-scale` setting
  (default `0.8`) accounts for weaker-feeling knockback over touch input.

### Performance
- **Faster packet handling** — the check pipeline now uses cached dispatch
  lists instead of rebuilding them on every packet.
- **Lighter movement tracking** — per-move scans are throttled and skip
  redundant world lookups (notably for players not in vehicles).

### New config options
- `checks.world.blockreach.window-size` (8), `required-flags` (3),
  `minimum-excess` (0.05)
- `checks.world.fastbreak.ignore-above-ms` (10000.0)
- `checks.combat.velocity.bedrock-knockback-scale` (0.8)

### Upgrade notes
- Version is now `1.0.1`; deploy artifact is `Guardian-1.0.1.jar`.
- No config migration needed — new keys fall back to documented defaults, and
  missing keys are merged in automatically on first start.

---

## v1.0.0 — initial release

- 31 detections across combat, movement, world, player and packet categories.
- Violation-level system with decay, clean-play credit and tiered punishments.
- Crossplay-aware thresholds for Bedrock (Geyser/Floodgate) players.
- Memory, SQLite and MySQL violation history plus `/guardian` staff commands.
- Optional integrations: LuckPerms, Vault, PlaceholderAPI, Discord webhooks,
  bStats metrics, and a movement-replay testing tool.

