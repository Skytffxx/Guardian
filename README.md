# Guardian — Complete Reference

A crossplay-aware, low-false-positive anticheat for Paper/Spigot servers.

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Module architecture](#2-module-architecture)
3. [Check inventory](#3-check-inventory)
4. [Data flow](#4-data-flow)
5. [Build and deployment](#5-build-and-deployment)
6. [Configuration reference](#6-configuration-reference)
7. [False-positive mitigation](#7-false-positive-mitigation)
8. [Known limitations](#8-known-limitations)
9. [Tuning guide](#9-tuning-guide)
10. [Developer notes](#10-developer-notes)
11. [Command reference](#appendix-a--command-reference)
12. [Placeholders](#appendix-b--placeholderapi-placeholders)
13. [Permissions](#appendix-c--permissions)

---

## 1. Project overview

| Field | Value |
|---|---|
| Name | Guardian |
| Author | skyzzz |
| Category | Server-side anticheat / anti-exploit |
| Native build target | Paper 1.21.11 |
| Supported range | 1.21.11 → 26.1 → 26.2 → 26.3 → future drops |
| Java runtime | Java 21 minimum; `26.1`+ raises to Java 25 |
| Server software | Paper (primary), Purpur, Folia (region-threaded) |
| Packet layer | PacketEvents 2.13+ |

Guardian's design goal is **fewer false positives than comparable anticheats** while catching the same cheats. Every detection is a rolling-window evaluation, no check punishes off a single packet, and all timing thresholds scale with the player's ping and the server's TPS.

---

## 2. Module architecture

Three Maven modules, one deployed jar.

```
guardian-parent (pom)
├── guardian-api        — zero-Bukkit contracts and data model
├── guardian-checks     — all detection classes
└── guardian-core       — plugin entry point, listeners, config, storage
```

### Dependency rules

| Module | Sees Bukkit? | Sees PacketEvents? | Purpose |
|---|---|---|---|
| `guardian-api` | No | No | Contracts; safe to unit test |
| `guardian-checks` | **No** | Yes (packet-level only) | Detections |
| `guardian-core` | Yes | Yes | Plugin bootstrap, everything else |

### Why the split matters

`guardian-checks` **physically cannot import `org.bukkit`**. That guarantees:

1. Checks cannot reach into the live server. All gameplay state is fed to them via `PlayerProfile` attributes.
2. Data structures (`RollingWindow`, `PositionHistory`, `ClickHistory`, `ViolationLevel`) are testable without booting a server.
3. Disabling one check cannot touch unrelated state.
4. A future `guardian-proxy` module (Velocity/BungeeCord) is an add, not a rewrite.

This is enforced by the compiler, not by convention.

### Directory layout

```
guardian/
├── pom.xml                         — parent / aggregator
├── guardian-api/
│   └── src/main/java/com/skyzzz/guardian/api/
│       ├── GuardianAPI.java
│       ├── Platform.java
│       ├── PacketDirection.java
│       ├── check/                  — Check, AbstractCheck, CheckRegistry, CheckSettings, CheckCategory
│       ├── data/                   — MoveData, AttackData, DamageData, BlockPlaceData, BlockBreakData, PacketData
│       ├── player/                 — PlayerProfile, ProfileManager, PositionHistory, ClickHistory
│       ├── violation/              — ViolationLevel, ViolationRecord, ViolationStore
│       ├── event/                  — GuardianFlagEvent, GuardianPunishEvent
│       └── util/                   — MathUtil, RollingWindow
├── guardian-checks/
│   └── src/main/java/com/skyzzz/guardian/checks/
│       ├── combat/                 — 6 checks
│       ├── movement/               — 11 checks
│       ├── world/                  — 7 checks
│       ├── player/                 — 4 checks
│       ├── packet/                 — 4 checks
│       └── CheckBootstrap.java
└── guardian-core/
    └── src/main/
        ├── java/com/skyzzz/guardian/core/
        │   ├── GuardianPlugin.java
        │   ├── check/CheckRegistryImpl.java
        │   ├── config/              — GuardianConfig, BukkitCheckSettings, Messages
        │   ├── player/              — GuardianProfile, GuardianProfileManager, ProfileListener
        │   ├── packet/              — GuardianPacketListener, PacketEventsHook
        │   ├── punish/PunishmentManager.java
        │   ├── alert/               — AlertManager, DiscordWebhook
        │   ├── command/GuardianCommand.java
        │   ├── storage/             — MemoryViolationStore, SqlViolationStore
        │   ├── integration/         — Floodgate, LuckPerms, Vault, PlaceholderAPI, bStats
        │   ├── replay/ReplayRunner.java
        │   └── util/                — Schedulers, Text
        └── resources/
            ├── plugin.yml
            ├── config.yml
            └── messages.yml
```

---

## 3. Check inventory

**31 checks total. All 31 fully implemented.**

### Combat (6)

| Check | Detection method | Key thresholds |
|---|---|---|
| `killaura` | Multi-target in one tick, no-swing attacks, through-wall attacks | `multi-target-streak: 2`, `no-swing-streak: 4`, `through-wall-streak: 3` |
| `reach` | Real hitbox-vs-eye distance, ping-compensated | `max-reach: 3.0`, rolling window of 12, `required-flags: 5` |
| `autoclicker` | Coefficient of variation + duplicate-interval density | `min-coefficient-of-variation: 0.075`, `max-duplicate-ratio: 0.35` |
| `aim` | GCD of rotation deltas | `min-gcd: 0.009`, `required-above-ratio: 0.72` |
| `velocity` | Compares actual movement delta against expected from outbound velocity packet | `minimum-ratio: 0.55`, `sample-window: 6` |
| `fakecriticals` | Hooks damage event, reconstructs trajectory from raw move history to prove crit was physically impossible | `minimum-reconstructed-fall: 0.08`, `maximum-fall-inflation: 3.0` |

### Movement (11)

| Check | Detection method | Key thresholds |
|---|---|---|
| `speed` | Horizontal speed with all modifiers modelled | `base-sprint: 0.288`, `required-flags: 6` |
| `fly` | Hover detection + no-gravity-acceleration detection | `hover-ticks: 14`, `gravity-violations: 12` |
| `nofall` | False onGround claims over fall distance | `minimum-fall-distance: 1.5`, `required-flags: 3` |
| `jesus` | Hovering on liquid surface | `max-hover-delta-y: 0.02`, `required-flags: 10` |
| `phase` | Bounding volume intersecting solid blocks | `required-flags: 3` |
| `step` | Illegal step height with jump-boost exceptions | `max-step: 0.6`, `required-flags: 2` |
| `sneak` | Client/server sneak state desync during full-speed movement | `max-sneak-speed: 0.07`, `required-flags: 8` |
| `timer` | Tick-rate balance accumulation | `balance-threshold: 90.0` |
| `elytra` | Firework boost while not gliding, rapid boosts | `minimum-boost-gap-ticks: 8` |
| `vehicle` | Vehicle speed and illegal rise | `max-vehicle-speed: 0.9`, `air-streak: 8` |

### World (7)

| Check | Detection method | Key thresholds |
|---|---|---|
| `scaffold` | Line-of-sight placement, tower pitch, rapid cadence | `rotation-miss-streak: 4`, `tower-miss-streak: 4` |
| `fastplace` | Sustained sub-tick placement intervals | `minimum-interval-ms: 45.0` |
| `fastbreak` | Real vanilla break-time formula (tool speed, Efficiency, Haste, Aqua Affinity, airborne divisor) | `minimum-time-ratio: 0.75` |
| `nuker` | Breaks-per-tick + mean-breaks-per-tick | `max-breaks-per-tick: 1` |
| `autotool` | Optimal tool switch within same/adjacent tick | `reaction-window-ticks: 2`, `required-flags: 6` |
| `blockreach` | Block interaction distance | `max-reach: 4.5` |
| `xray` | Ore-ratio heuristic — **review-only, no auto-punish** | `ore-ratio-threshold: 0.55` |

### Player / inventory (4)

| Check | Detection method | Key thresholds |
|---|---|---|
| `fastuse` | Eat/drink duration from use-item packet | `expected-duration-ms: 1610.0` |
| `autorespawn` | Death-to-respawn reaction time | `minimum-reaction-ms: 150.0` |
| `regen` | Health regen cadence vs vanilla baseline | `minimum-heal-interval-ticks: 20` |
| `inventory` | Click rate + same-slot spam (both required) | `maximum-cps: 25`, `same-slot-streak: 8` |

### Packet / protocol (4)

| Check | Detection method | Key thresholds |
|---|---|---|
| `packetsanity` | NaN/Infinity, coordinate bounds, invalid pitch, tick distance | **Always enabled** — cannot be disabled via config |
| `flood` | Per-second packet rate with soft/hard limits | `soft-limit-per-second: 400`, `hard-limit-per-second: 900` |
| `clientbrand` | Mid-session brand change + signature match | `blocked-signatures` list |
| `pingspoof` | Keep-alive id matching + RTT vs declared ping | `wrong-id-streak: 3`, `ping-mismatch-factor: 3.0` |

---

## 4. Data flow

### Packet path

```
Player client
    │
    ▼
PacketEvents (async)
    │
    ▼
GuardianPacketListener
    ├── onPacketReceive ──▶ dispatch to checks (packet category first)
    ├── build MoveData / AttackData from packet
    │       └── world access (hitbox, LoS) hopped to main thread
    └── onPacketSend ──▶ record velocity, keep-alive
    │
    ▼
CheckRegistryImpl.dispatchX(profile, data)
    │
    ▼
Each enabled Check in that category
    │
    ▼
AbstractCheck.flag() → profile.flag() → ViolationLevel.add()
    │
    ▼
PunishmentManager.handleFlag() → alert + evaluateTiers()
```

### Per-tick path

```
GuardianPlugin.tickProfiles() (every tick)
    │
    ├── refresh TPS
    ├── refresh ping
    ├── refresh health
    ├── dispatchTick to all checks
    └── profile.advanceTick()
```

### Attribute feed path

`ProfileListener` writes gameplay state into `PlayerProfile` attributes so checks can read them without touching Bukkit:

| Event | Attributes written |
|---|---|
| `PlayerMoveEvent` | in-water, in-liquid, on-climbable, on-ice, on-slime, soul-speed, fall-distance, server-on-ground, vehicle, vehicle-x/y/z, server-sneaking, server-sprinting, feet-in-solid, head-in-solid, block-below-*, block-at-feet-*, tool-efficiency, tool-type, haste-amplifier, aqua-affinity, conduit-power |
| `PlayerItemHeldEvent` | last-item-switch-tick |
| `PlayerInteractEvent` (firework) | firework-boost-tick |
| `PlayerToggleSneakEvent` | client-sneaking |
| `PlayerToggleSprintEvent` | server-sprinting |
| `EntityDamageByEntityEvent` | fires `dispatchDamage` with `DamageData` |
| `EntityToggleGlideEvent` | elytra |
| `PlayerTeleportEvent` | teleport (grace-cleared after 5 ticks) |
| `PlayerDeathEvent` | forwards to `AutoRespawnCheck.onDeath` |

### Violation level

Each check has its own `ViolationLevel`:

- Starts at 0
- Increments by `vl-weight` (or a per-flag severity multiplier) on each flag
- Decays at `violations.decay-per-second` (default 0.35)
- Capped at `violations.ceiling` (default 1000.0)
- `clean-reward` subtracts VL when the check observes normal play

Punishments trigger on **VL thresholds**, never on a single flag.

---

## 5. Build and deployment

### Build

```bash
cd /workspaces/Guardian
mvn clean package
```

Reactor summary must show:

```
Guardian ............ SUCCESS
Guardian :: API ..... SUCCESS
Guardian :: Checks .. SUCCESS
Guardian :: Core .... SUCCESS
```

### Java 25 target (for 26.1+)

```bash
mvn clean package -Pjava25
```

### Faster iteration during check-writing

```bash
# Only rebuild the checks module and its dependencies
mvn -pl guardian-checks -am compile

# Keep-going mode: report all errors, don't stop at first module failure
mvn clean package -fae
```

### Deploy

**One jar** goes to the server:

```
guardian-core/target/Guardian-1.0.1.jar
```

Do **not** upload `guardian-api-*.jar` or `guardian-checks-*.jar` — they are already inside the Guardian jar. Do **not** upload `original-Guardian-*.jar` either; that is the pre-shade artifact.

Server `plugins/` directory should contain:

```
plugins/
├── Guardian-1.0.1.jar
├── packetevents-spigot-X.Y.Z.jar
└── Vault (1.7).jar    (optional)
```

Guardian **hard-depends** on PacketEvents. Without it, Guardian will not load.

### Verify the jar before upload

```bash
# Plugin class must be present
unzip -l guardian-core/target/Guardian-1.0.1.jar | grep GuardianPlugin

# All three packages must be merged
unzip -l guardian-core/target/Guardian-1.0.1.jar | grep -c "com/skyzzz/guardian/"
# Expected: a few hundred

# plugin.yml must be populated
unzip -p guardian-core/target/Guardian-1.0.1.jar plugin.yml | head -5
```

### Cache clearing

Paper remaps plugins into `plugins/.paper-remapped/` and does not always invalidate this cache. If you replace the jar and the server still loads the old copy:

```bash
rm -rf plugins/.paper-remapped/
```

Then restart.

### Expected startup output

```
[Guardian] Enabling Guardian v1.0.1
[Guardian] Guardian enabled — 31 checks registered, storage=MemoryViolationStore
```

---

## 6. Configuration reference

Two files: `config.yml` and `messages.yml`, both generated on first run under `plugins/Guardian/`.

### Top-level sections

| Section | Purpose |
|---|---|
| `performance` | History buffer sizes, async pool size, dispatch budget |
| `violations` | Decay rate, ceiling, clean reward |
| `false-positives` | Bedrock multiplier, TPS compensation, ping compensation, exemptions |
| `punishments` | Per-check, per-category, and global tiers |
| `alerts` | Throttle between alerts for the same check |
| `discord` | Webhook configuration |
| `storage` | MEMORY, SQLITE, MYSQL, or MARIADB |
| `checks` | All 31 checks, grouped by category |

### Configurability contract

**Configurable (85–90%)**:
- Per-check enable/disable
- Every threshold, streak count, window size
- VL weights, decay rates, clean rewards
- Punishment commands per check/category/tier
- Exemptions (worlds, gamemodes, permissions, regions)
- Bedrock multiplier
- Buffer sizes, thread pool size

**Hardcoded (10–15%)**:
- Core packet sanity validation (NaN, bounds, invalid pitch)
- Anti-tamper logic protecting the check pipeline
- Dispatch order and fail-safe that disables a throwing check

A fully config-editable sanity layer is a bypass, not a feature. `PacketSanityCheck` ignores its own `enabled: false` for this reason.

### Exemptions

```yaml
false-positives:
  exemptions:
    gamemodes: [CREATIVE, SPECTATOR]   # always exempt from combat/movement
    worlds: []                          # world names
    permissions: [guardian.exempt]      # permission nodes
    regions: []                         # WorldGuard region ids
```

Gamemode exemptions apply **always**, regardless of the config.

### Punishments

Commands fire from console, so any command-based punishment plugin works:

```yaml
punishments:
  checks:
    killaura:
      tiers:
        - threshold: 15.0
          cooldown-seconds: 180
          commands:
            - "tempban {player} 3d Killaura ({check} VL {vl})"
```

Placeholders: `{player}`, `{uuid}`, `{check}`, `{vl}`.

Cooldowns prevent a lag-induced VL spike from firing the same tier repeatedly.

### Storage

```yaml
storage:
  type: MEMORY     # MEMORY | SQLITE | MYSQL | MARIADB
  pool-size: 4
  sqlite:
    file: violations.db
  mysql:
    host: 127.0.0.1
    port: 3306
    database: guardian
    username: root
    password: ""
```

Storage is optional. Guardian runs fine on the in-memory store. SQLite bundles a ~22 MB native driver — drop the dependency if you never use it.

### Messages

`messages.yml` uses MiniMessage format. Supports hex colors, gradients, hover events, and click events:

```yaml
alert-format: "<dark_gray>[<gradient:#ffcc00:#ff6600><bold>G</bold></gradient><dark_gray>] <white>{player} <gray>failed <yellow>{check} <gray>VL <red>{vl}"
```

---

## 7. False-positive mitigation

Six mechanisms, all in place by default:

### 1. Multi-signal corroboration
No check flags from a single anomalous packet. Autoclicker requires **both** low CV **and** high duplicate-interval density. Inventory requires **both** excessive CPS **and** same-slot spam. Fakecriticals requires the reconstruct signal to agree with the server's crit flag.

### 2. Rolling-window evaluation
Movement and combat checks evaluate the last N ticks and require consistent anomalies. Reach uses a 12-sample window with `required-flags: 5`. Speed uses a 12-tick window with `required-flags: 6`.

### 3. TPS + latency compensation
Every timing threshold scales with current TPS. Reach and block-reach distances scale with ping. A lag spike cannot read as a cheat.

### 4. Bedrock leniency profiles
Geyser/Floodgate players get thresholds **multiplied** by `false-positives.bedrock-threshold-multiplier` (default 1.35), not exemption. Touch input produces different aim, placement timing, and combat rhythm — the check still runs, just with wider tolerance.

### 5. Exemptions
Per-world, per-gamemode, permission-based, and region-based. Creative and spectator are **always** exempt from combat and movement checks.

### 6. Review-before-punish
The `/guardian profile` command shows live VL per check. Every flag produces a human-readable debug string (e.g. `dist=3.142 allowed=3.083 excess=0.059 ping=42ms`). Xray is explicitly review-only.

---

## 8. Known limitations

Three checks are honest approximations rather than perfect validators:

### `fakecriticals` — protocol limitation
The Minecraft protocol does not carry a "this is a crit" flag. The check hooks the **damage event** and reconstructs the client's trajectory from raw move packets. If the server awarded a crit but the trajectory proves the player could not have fallen, the crit was faked. This is the closest signal available at the protocol level. It is not a perfect crit validator.

### `fastbreak` — formula coverage
The check uses the real vanilla break-time formula (tool speed, Efficiency `level²+1`, Haste multiplier, Aqua Affinity divisor, airborne divisor, conduit power multiplier). But it does not cover every edge case:
- Custom blocks with modified hardness on modded servers
- Mods that alter break mechanics
- Unusual block/tool/enchantment combinations

Config ships `minimum-time-ratio: 0.75` for the first week — raise toward 0.85 if you see false positives on normal mining.

### `velocity` — sample-window sensitivity
Knockback is absorbed legitimately by blocks, water, ladders, and elytra. The check exempts these cases. But a client with unusual network conditions can also appear to ignore knockback. Config's `minimum-ratio: 0.55` requires the actual movement to be less than 55% of expected over 6 ticks — conservative.

### `regen` — needs per-tick health
Regen cadence cannot be measured if health is not updated every tick. As of the current build, `GuardianPlugin.tickProfiles()` refreshes health every tick so the check works even when a player is standing still.

---

## 9. Tuning guide

### Order of operations

**1. Get `false-positives.*` right first.**

These are the multipliers that make every other threshold work. If your server runs at 18 TPS average, lower `tps-compensation.maximum-factor`. If your Bedrock population struggles, raise `bedrock-threshold-multiplier`.

**2. Set `violations.decay-per-second`.**

This is the single strongest lever against false positives. Higher decay = a lag spike bleeds off faster before it can matter. Default 0.35 is conservative — if you see chained flags after one network stutter, raise to 0.5.

**3. Tune `checks.*` thresholds last, with debug on.**

Run `/guardian debug <check>` in-game, then play normally for 10–20 minutes. The debug output shows the actual values passing through the check. Set the threshold just above the highest legitimate value you observe.

**4. Configure `punishments.*` after one week of alerts.**

Do not enable punishment tiers until you have watched a week of alert traffic per check.

### Rules of thumb

| Symptom | Fix |
|---|---|
| Check fires on lag spikes | Raise `tps-compensation.maximum-factor` or lower the check's `minimum-*` threshold |
| Check fires on high-ping players | Raise `ping-compensation.maximum-*` |
| Check fires on Bedrock players | Raise `false-positives.bedrock-threshold-multiplier` |
| Check never fires on known cheaters | Lower the check's `minimum-*` threshold, or lower `required-flags` |
| Check fires on one specific block/tool combo | Raise `fastbreak.minimum-time-ratio`, or whitelist the block in the check |
| VL climbs too fast during normal play | Raise `violations.decay-per-second` |

### Replay-before-punish

`ReplayRunner` reads a text file of recorded movement packets and replays it through the check pipeline. It reports how many records produced a violation — that is your false-positive rate on legitimate gameplay.

Format (one record per line):

```
MOVE,x,y,z,lastX,lastY,lastZ,yaw,pitch,lastYaw,lastPitch,onGround,lastOnGround,deltaNanos
TICK,deltaNanos
```

Use it to measure threshold changes before deploying.

### Safe thresholds for common servers

| Server type | Adjustments |
|---|---|
| Vanilla survival | Defaults are fine |
| Skyblock / oneblock | Disable `xray`, lower `fly.minimum-air-ticks` |
| PvP / factions | Lower `reach.max-reach` to 3.1, raise `autoclicker.min-coefficient-of-variation` to 0.09 |
| Bedrock-only | Raise `bedrock-threshold-multiplier` to 1.5 |
| Laggy host (< 18 TPS) | Raise `tps-compensation.maximum-factor` to 2.5 |
| Creative plots | Already exempt — verify `false-positives.exemptions.gamemodes` includes CREATIVE |

---

## 10. Developer notes

### Adding a new check

1. Create `guardian-checks/src/main/java/com/skyzzz/guardian/checks/<category>/MyCheck.java`
2. Extend `AbstractCheck`, pass name and category to `super()`
3. Override only the hooks you need
4. Register in `CheckBootstrap.create()`
5. Add a `checks.<category>.<name>` section to `config.yml`
6. Add `punishments.checks.<name>: { tiers: [] }` if it should not auto-punish

You **cannot** import `org.bukkit` in a check. If you need world state, add an attribute to `PlayerProfile` and populate it from `ProfileListener`.

### Adding a new attribute feed

1. Add the write in `ProfileListener` (appropriate event handler)
2. Add the read in the check via `profile.attribute("key")`
3. Document the key in this file under "Data flow"

### The `AbstractCheck` protected helpers

| Helper | Purpose |
|---|---|
| `d(key, def)` | Read a double from check config with default |
| `i(key, def)` | Read an int from check config with default |
| `b(key, def)` | Read a boolean from check config with default |
| `s(key, def)` | Read a string from check config with default |
| `scaled(profile, key, base)` | Read a double and multiply by the player's threshold scale (Bedrock multiplier) |
| `flag(profile, debug, args)` | Raise VL with the configured `vl-weight` |
| `flag(profile, weight, debug, args)` | Raise VL with `vl-weight * weight` |
| `reward(profile, amount)` | Subtract VL for clean behaviour |

### Debug output format

Every flag produces a formatted debug string that prints under `/guardian debug <check>`. Example:

```
[reach] +1.50 -> 4.50 | dist=3.142 allowed=3.083 excess=0.059 ping=42ms over=5/12
```

Format: `[check-name] +<weight> -> <new-vl> | <debug string>`

Keep debug strings informative — they are the primary tool staff use to judge whether a flag is legitimate.

### The fail-safe

`CheckRegistryImpl.safe()` wraps every dispatch call. If a check throws, it is logged once and disabled. One broken check cannot take down the whole pipeline.

### Threading model

| Operation | Thread |
|---|---|
| Packet parsing | Async (PacketEvents worker) |
| Attribute reads from `PlayerProfile` | Same thread as the check |
| World state access (`player.getLocation()`, `hasLineOfSight`) | Main thread, hopped via `Schedulers.runSync` |
| Database writes | Async (single-threaded queue in `SqlViolationStore`) |
| Discord webhook | Async (single-threaded executor in `DiscordWebhook`) |
| Config reload | Main thread |

`GuardianProfile.attributes` is a `ConcurrentHashMap`, so attribute reads/writes are safe from any thread.

---

## Appendix A — Command reference

| Command | Permission | Purpose |
|---|---|---|
| `/guardian help` | `guardian.command` | Show command list |
| `/guardian profile <player>` | `guardian.command.profile` | Live check status + VL breakdown |
| `/guardian check <name> <on\|off>` | `guardian.command.check` | Toggle a check |
| `/guardian alerts` | `guardian.alerts` | Toggle live flag notifications for yourself |
| `/guardian debug <check\|all>` | `guardian.command.debug` | Enable verbose per-check values |
| `/guardian history <player> [limit]` | `guardian.command.history` | Violation log |
| `/guardian reload` | `guardian.command.reload` | Reload `config.yml` + `messages.yml` |

## Appendix B — PlaceholderAPI placeholders

| Placeholder | Returns |
|---|---|
| `%guardian_vl_total%` | Sum of VL across all checks |
| `%guardian_vl_<check>%` | VL for one check |
| `%guardian_platform%` | `JAVA` or `BEDROCK` |
| `%guardian_checks%` | Number of registered checks |
| `%guardian_enabled_<check>%` | `true` / `false` |

## Appendix C — Permissions

| Permission | Default | Purpose |
|---|---|---|
| `guardian.command` | op | Base command access |
| `guardian.command.profile` | op | View live profile |
| `guardian.command.check` | op | Toggle checks |
| `guardian.command.debug` | op | Verbose mode |
| `guardian.command.history` | op | Browse history |
| `guardian.command.reload` | op | Reload config |
| `guardian.alerts` | op | Receive live alerts |
| `guardian.exempt` | false | Fully exempt from all checks |
| `guardian.exempt.combat` | false | Exempt from combat checks |
| `guardian.exempt.movement` | false | Exempt from movement checks |
| `guardian.exempt.world` | false | Exempt from world checks |
| `guardian.exempt.player` | false | Exempt from player checks |
| `guardian.exempt.packet` | false | Exempt from packet checks |

---

**Guardian 1.0.1** — skyzzz