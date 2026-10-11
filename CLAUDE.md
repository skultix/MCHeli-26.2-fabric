# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MCHeli is EMB4's vehicle mod for Minecraft 1.7.10 (helicopters, planes, tanks, vehicles, weapons). This repo is a fork of the existing **NeoForge 1.21.1** port ([Modrinth: mcheli-modern-port](https://modrinth.com/mod/mcheli-modern-port), upstream source [ThatSoulyGuy/MCHeli](https://github.com/ThatSoulyGuy/MCHeli)).

**The goal of this fork is to port the mod to Fabric for Minecraft 26.2**, which runs on Java 25. That is why the repo is named `MCHeli-26.2-fabric`. The NeoForge module is the starting point and working reference. The original 1.7.10 mod's decompiled source is in `reference/decompiled-srg/mcheli/`.

The NeoForge port's own description gives its starting state (alpha):
- Working: 112 vehicles, flight and drive physics, weapons (heat, rockets, guided missiles with lock-on, bombs, torpedoes, flares), crew and gunner seats with seat switching, per-part damage, the cockpit HUD, free-look, camera cycling, rotor folding, and content packs placed in `.minecraft/mcheli/`.
- Missing: survival recipes (vehicles can only be spawned in creative), fireable weapons on some recon aircraft, UAV drones, and night/thermal vision.

## Dev environment

`devenv.nix` provides JDK 8, 21 and 25 and the native libraries that `runClient` needs on NixOS. Enter it with `devenv shell`. It exports `JDK8`, `JDK21` and `JDK25`, and `gradle.properties` (`org.gradle.java.installations.fromEnv`) points Gradle's toolchain discovery at them. Outside devenv, Gradle falls back to downloading JDKs through foojay. The shell also defines `test-agnostic`, `run-neoforge-client`, `run-neoforge-server`, `run-fabric-client` and `run-fabric-server` as shortcuts. Dev servers need `eula=true` in `<module>/run/eula.txt`, and `run/` is gitignored.

## Build & test

The Gradle wrapper is 9.7.1, the minimum Loom 1.18.4 accepts. Each subproject declares its Java version through toolchains: 21 for the agnostic and NeoForge modules, 8 for the 1.7.10 reference, and 25 for Fabric 26.2.

```
./gradlew :mcheli-agnostic:test                                   # unit tests (pure Java, no Minecraft)
./gradlew :mcheli-agnostic:test --tests 'mcheli.agnostic.sim.HeliFlightModelTraceTest'   # single test class
./gradlew :mcheli-agnostic:test --tests '*WeaponAimTest.someMethod'                      # single method
./gradlew :neoforge-1.21.1:build                                  # mod jar (agnostic jar embedded via jarJar)
./gradlew :neoforge-1.21.1:runClient
./gradlew :neoforge-1.21.1:runServer
./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests            # headless in-game self-test, halts after the verdict
./gradlew :fabric-26.2:build                                      # Fabric mod jar (agnostic embedded via include)
./gradlew :fabric-26.2:runClient
./gradlew :fabric-26.2:runServer --args=nogui
./gradlew :fabric-26.2:runServer -PmcheliSelfTests=heli,vtol     # headless self-tests by name (true = crush, all = every test)
./gradlew :fabric-26.2:runClientGameTest                          # scripted client smoke test; screenshots under fabric-26.2/build/run/clientGameTest
./gradlew :fabric-26.2:prodServer                                 # the BUILT jar on a real Fabric server install (production check)
```

There is no linter. Do not enable `org.gradle.configuration-cache` globally (see `gradle.properties`).

## Module layout

- **`mcheli-agnostic/`**: the game logic in plain Java, with no Minecraft, NeoForge or LWJGL on the classpath. The compiler enforces this, so a platform import here fails to build. It contains the config DSL parsers (`*Info` / `*InfoManager`), the `eval` expression engine, model parsing (`.mqo`/`.obj`), flight and drive simulation (`sim/`), weapons, ballistics and missile guidance (`weapon/`), and HUD definitions. JUnit tests live here. Many are tick-by-tick "trace" tests that check behaviour against the original 1.7.10 entity code.
- **`mcheli-agnostic/.../spi/`**: the boundary interfaces (`WorldView`, `EntityRef`, `ResourceSource`, `ModelHandle`, `Network`, `SyncedData`, `RandomSource`, `Logger`, …). Agnostic code talks to the game only through these.
- **`neoforge-1.21.1/`** (package `mcheli.dependent`, entrypoint `mcheli.MCHeli`): the platform layer. `port/` implements the SPI with NeoForge types (`Neo*` classes). It also holds entities, renderers, networking payloads (`control/`), registries, items, the menu, and one client mixin (`CameraMixin`, registered in `mcheli.mixins.json`).
- **`fabric-26.2/`** (entrypoints `mcheli.MCHeli` / `mcheli.MCHeliClient`): the Fabric 26.2 port. It keeps the NeoForge module's `mcheli.dependent.*` packages and class names (including the `Neo*` SPI implementations) so the two modules stay diffable; only names the 26.x API forces were changed. NeoForge event subscribers became explicit `register()` calls from the entrypoints. Hooks with no Fabric event are mixins in `mcheli.dependent.mixin` (`mcheli.mixins.json`): `BuiltInPackSourceMixin` (content-pack and recipe packs), `CameraMixin`, `MinecraftMixin` and `EntityRenderDispatcherMixin`. `mcheli.classtweaker` opens `RenderType.create`. It uses Loom `splitEnvironmentSourceSets()`, so client-only code goes in `src/client/java`, and Loom fails the build if `src/main` references client classes. Versions are pinned in `fabric-26.2/gradle.properties`.
- **`mcheli-assets/`**: MCHeli's own content, shared by every platform module through `sourceSets.main.resources.srcDir`. That covers the vehicle, weapon and HUD `.txt` configs, `.mqo`/`.obj` models, textures, sounds, `sounds.json` and lang. It is a plain folder, not a Gradle project. Anything loaded through Minecraft's resource system (textures, sounds) must have a lowercase `[a-z0-9/._-]` path, or it is silently dropped. The `drafting_table_desc` images were therefore renamed from the original `<item>#<n>.png` to `<item>_<n>.png`, which matters when the drafting table gets ported. The `.txt` configs are read by MCHeli itself, so their case doesn't matter. Vanilla-format JSON whose format changes between MC versions stays in each module's own `src/main/resources`: item models and client items, particle definitions, and recipes.
- **`mcheli-1.7.10/`**: the decompiled, deobfuscated and runnable original (RetroFuturaGradle, Java 8). It is the **ground truth** for porting behaviour. It is currently commented out in `settings.gradle`; its README explains how it was produced and how to run it. It needs the original `MC1.7.10_mcheli_1.0.3.zip`, which is not committed (`-PmcheliJar=...`).
- **`reference/decompiled-srg/mcheli/`**: the raw CFR decompile of the original mod, with SRG names (`func_*` / `field_*`). It is reference material, not a Gradle project. For readable MCP names, use `mcheli-1.7.10/src/main/java/` instead.

For the Fabric port, the intended split is that `mcheli-agnostic` is reused unchanged (its Java 21 bytecode runs on 25) and `fabric-26.2` implements the same SPI, the way `neoforge-1.21.1` does. **Read `docs/porting/` before porting anything.** It holds the NeoForge→Fabric API mapping, the vanilla 1.21.1→26.2 change checklist, and 26.2 facts checked against the real jars. Minecraft 26.x is unobfuscated, so the NeoForge code's Mojang names mostly carry over. To check a 26.2 vanilla signature, run `javap -p` on `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-{common,clientonly}-deobf/26.2/*.jar`.

Code in the agnostic layer often keeps the original class names (`MCH_WeaponInfo`, `MCH_HeliInfoManager`, `MCP_PlaneInfoManager`) so it can be cross-referenced with the 1.7.10 source. Keep that convention when porting more classes.

## Key wiring (neoforge `MCHeli` constructor → `commonSetup`)

1. `MchContentPacks.discover()` runs first, before the registries freeze. It scans `<gamedir>/mcheli/<pack>/`, where content sits either flat or under `assets/mcheli/`. It builds a `CompositeResourceSource` that combines the bundled assets and the packs.
2. `MchRegistries.registerVehicles()` creates one spawn item per vehicle config found. It must run before `register(modEventBus)`.
3. `MchSounds` registers a `SoundEvent` per pack `.ogg`. `MchPackResourcesFinder` exposes pack textures, a generated `sounds.json` and the `.ogg` files to the vanilla resource manager.
4. In `commonSetup`, every `*InfoManager.load(res, log, "<dir>")` parses configs through that same `ResourceSource`.

Assets travel by two routes: configs and models go through the agnostic `ResourceSource`, while textures, sounds and `sounds.json` go through the vanilla client resource manager. The original mod loaded content from disk with `java.io.File`. The port must load it through the resource system.

Bundled content is under `mcheli-assets/assets/mcheli/`, described above.

## Gradle gotchas

- ModDevGradle and Loom versions are declared **once**, in the root `build.gradle` (`apply false`). Subprojects apply them without a version. Bumping either plugin may require a newer Gradle wrapper; Loom 1.18.4 needs 9.7.0 or newer.
- Settings-level plugins share a classloader with every Minecraft plugin. foojay-resolver 0.7.0 put Gson 2.9.1 there, which broke Loom's Minecraft setup (`Unexpected IllegalAccessException occurred (Gson 2.9.1)`). Keep foojay at 1.0.0 or newer.
- In `neoforge-1.21.1/build.gradle`, do not add a `repositories {}` block. Doing so disables the NeoForge repos that ModDevGradle manages; repositories belong in `settings.gradle`.
- In Loom (Fabric), `implementation project(':mcheli-agnostic')` covers both compile and dev runtime, and `include` embeds it in the production jar. Loom 26.x does not remap, so use plain `implementation` and `jar`, not `modImplementation` or `remapJar`.
- The agnostic dependency has to be declared three ways: `implementation` (compile), `additionalRuntimeClasspath` (dev runs, otherwise `ClassNotFoundException`), and `jarJar` (production jar).
- Do not put a `version { strictly ... }` block on the project dependency. It overwrites `project.version`.

## Weapon damage config (Fabric)

`config/mcheli-damage.json` tunes every MCHeli weapon's damage on the server: global, per-weapon-type and per-target multipliers, per-weapon overrides of `Power`/`Explosion`/`ExplosionBlock`/`ExplosionInWater`/`DamageFactor`, and the TaCZ rules. `/mcheli damage reload` re-reads it. The user-facing reference is `docs/configuring-damage.md`; keep it in sync. The data model and its resolution logic are `mcheli.agnostic.weapon.MCH_DamageSettings` (unit-tested in `DamageSettingsTest`), and `mcheli.dependent.MchDamageConfig` reads and writes it with Gson. Overrides are written into the loaded `MCH_WeaponInfo`s and always start from the config values (`MCH_DamageSettings.original`). The multipliers are applied where damage is dealt: `MchBullet.onImpactEntity` and `MchExplosion.explodeWeapon`. A bullet's `damageWeapon` is the weapon whose tuning applies; airstrike bombs carry the CAS or marker weapon that called them in. `MchDamageConfigGameTest` checks it in game.

## Other-mod interop (Fabric)

`mcheli.dependent.compat.TaczCompat` puts TaCZ Refabricated guns on MCHeli's damage scale. Plain (non-explosive) bullets do nothing to armored vehicles (config `ArmorMinDamage` ≥ 5, i.e. the 9 real tanks) and half damage to everything else (all three numbers are in the `tacz` block of the damage config). Explosive ammo (RPG, grenades, any gun with the HE ammo mod) and explosions damage everything. It has no compile dependency; TaCZ is detected and read by reflection. For a rocket that hits a vehicle directly, the vehicle takes the bullet damage plus the ammo's designed `explosion.damage`, and that rocket's own follow-up blast is ignored. The 26.2 TaCZ port's `ExplodeUtil` drops the designed damage and makes a vanilla radius-only explosion, so a near-miss blast is rescaled to the designed value. That rescaling switches itself off if TaCZ uses its own `ProjectileExplosion` again, as upstream does. Everything still goes through the vehicle's armor formula; MCHeli's own explosions use nearly the vanilla formula, so TNT and other vanilla blasts need no adjustment.

**Hitboxes.** A vehicle's entity box stays the small physics core (`bodyWidth × bodyHeight`, default 2 × 0.7), which the flight and ground simulation rely on. Projectile hits use `AbstractMchVehicle.clipParts`, which tests three things:
- a model-built hull (`mcheli.agnostic.physics.HullShape`): the drawn model minus rotor and propeller blades, voxelized at 0.5 blocks and merged into boxes, then tested in the vehicle's local frame so it follows yaw, pitch and roll;
- the core;
- the config `BoundingBox` parts, which still decide armor and weak-point damage factors.

MCHeli bullets, vanilla projectiles (`ProjectileUtilMixin`) and TaCZ bullets (`TaczEntityUtilMixin`, `@Pseudo`, applied only when TaCZ is loaded) all use it. `HullShape.drawnPartsLower` is the single "which groups are drawn" rule, shared with the renderer. `MchHitboxGameTest` ray-checks every vehicle's hull.

To test against real TaCZ in dev, put the TaCZ, ForgeConfigAPIPort and PlayerAnimationLib jars in `fabric-26.2/compat-mods/` (gitignored, loaded via `localRuntime`). `MchTaczGameTest` then fires real RPG-7 and AWP rounds; it proves each round fired and passed through its target, and checks the damage (RPG-7 vs m1a2 121; AWP vs m1a2 0, vs ah-64 and growler about 20). Without the jars it skips.

## Commits

Commit regularly at logical checkpoints, after the build or test is verified. Do **not** add `Co-Authored-By` or any other AI attribution to commit messages.

## In-game self-tests

`mcheli.dependent.Demo*SelfTest` classes are game-bus listeners that spawn probe entities and log a pass/fail verdict. They run only in dev (`!FMLEnvironment.production`). Most are commented out in the `MCHeli` constructor; uncomment one to run it. `DemoCrushSelfTest` is gated by the `mcheli.selftests` property instead (`-PmcheliSelfTests`).

On Fabric the same classes implement `MchSelfTest`, and `-PmcheliSelfTests=<name,...>` selects them by name (the list is in `mcheli.MCHeli#registerSelfTests`). They are the port's acceptance tests. 26.x has no always-loaded spawn chunks, so before any test starts the harness loads a simulation ticket over the spawn area and waits for it. Only `crush` stops the server by itself; the others just log a verdict (`[...-SELFTEST] RESULT: PASS|FAIL`, or `[CREW/FUEL SELF-TEST] ALL PASS`). Run tests one at a time on a fresh world; run together, they interfere. Current Fabric status, each run alone: 15 pass. `contentpack` needs two synthetic packs written before boot; `docs/porting/README.md` explains how. `tank`, `plane` and `weapon` (an NPE) fail identically on NeoForge, because they are out of date against the current agnostic code. `flare` fails only on Fabric, and that is a test bug the port exposes: its guided missile has no shooter, so guidance discards it on the first tick. On 1.21.1 the missile sat outside the entity-ticking spawn chunks and never ticked. Compare against NeoForge before treating a failure as a regression.
