# Guardian — Update Log

Every release and what changed. Newest first.

---

## v1.0.1 — 2026-09-19

Bug-fix + performance + Bedrock release. All 31 checks verified against the
full `src/` deep scan.

### Bug fixes
- **`AbstractCheck.b()` ignored its default** — returned `false` instead of the
  caller's default when settings were unbound. `XrayHeuristicCheck`'s
  `review-only` gate now works correctly.
- **`FastUseCheck` was dead** (`last-use-duration-ms` never set anywhere).
  Wired end-to-end: `onInteract` stamps `use-start-nanos` for edibles /
  potions / milk / honey, new `PlayerItemConsumeEvent` handler computes the
  duration and dispatches a synthetic `USE_ITEM` packet so the check fires.
- **World pipeline was dead** — nothing ever called `dispatchBlockPlace` /
  `dispatchBlockBreak`, so `scaffold`, `fastplace`, `fastbreak`, `nuker`,
  `autotool`, `blockreach`, `xray` never received events. New
  `onBlockPlace` / `onBlockBreak` handlers in `ProfileListener` build
  `BlockPlaceData` / `BlockBreakData` (center-of-block eye distance,
  `hasLineOfSight(Location)`, face id from against→placed delta) and dispatch.
- **`NukerCheck.instantBreak` was dead** — `BlockBreakData.instantBreak` was
  always `false`. Core now resolves it via hardness-0 + flora/redstone list
  (`isInstantBreak`), and `NukerCheck` skips instant-break blocks.
- **`BlockReachCheck` half-wired + instant-flag** — only handled places and
  flagged on the first offence with no window. Now handles places **and**
  breaks (break distance via `last-break-eye-distance` attribute), uses a
  `RollingWindow(8)` requiring 3/8 over `minimum-excess 0.05`, with
  clean-reward decay. New config keys: `window-size`, `required-flags`,
  `minimum-excess`.
- **Packet-name matching was version-fragile** — `packetName().contains(...)`
  fails on PacketEvents `UPPER_SNAKE` names (`KEEP_ALIVE` vs `KeepAlive`).
  New `PacketData.matchesName()` normalizes both sides (lowercase, strip
  non-alphanumerics). Migrated: `fastuse`, `autorespawn`, `inventory`,
  `clientbrand`, `pingspoof` (send + receive).
- **`FastBreakCheck` stale-interval false positives** — new
  `ignore-above-ms: 10000` guard resets the streak on teleport / world-change /
  long-pause gaps.

### Bedrock / hooks
- `FloodgateHook`: **Geyser-Spigot standalone fallback** (`connectionByUuid`
  reflectively, no hard dep) + **30 s per-player TTL cache** + `invalidate()`
  on quit + cache cleared on reload.
- New `GuardianProfileManager.refreshPlatform()`; `reloadEverything()` refreshes
  online players' platform/scale so Floodgate/Geyser installs are picked up
  without rejoin.
- `VelocityCheck`: new `bedrock-knockback-scale: 0.8` config — Bedrock knockback
  reads weaker through touch-input latency.

### Performance
- `CheckRegistryImpl`: **cached dispatch snapshots** rebuilt only on
  register/unregister/reload/toggle/auto-disable — hot path no longer walks
  category maps per packet.
- `ProfileListener.onMove` (hottest handler): **25 ms throttle** + block-change
  trigger, single `getLocation()` reused, vehicle `getLocation()` skipped when
  not riding.
- **Teleport-grace task leak fixed** — old code spawned a never-cancelled
  repeating task per teleport; now a one-shot delayed clear via new
  `Schedulers.runSyncDelayed()` (Folia-safe).

### Version
- `1.0.0-SNAPSHOT` → `1.0.1` across parent + 3 module poms and README.
- Deploy artifact is now `guardian-core/target/Guardian-1.0.1.jar`.

---

## v1.0.0-SNAPSHOT — initial development baseline

- 3-module layout (`guardian-api` / `guardian-checks` / `guardian-core`),
  31 checks via `CheckBootstrap`, PacketEvents pipeline, VL + tiered
  punishments, memory/SQLite/MySQL stores, `/guardian` command suite,
  Floodgate/LuckPerms/Vault/PlaceholderAPI/bStats integrations, replay harness.
