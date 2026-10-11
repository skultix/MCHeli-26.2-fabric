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
```

There is no linter. Do not enable `org.gradle.configuration-cache` globally (see `gradle.properties`).

## Module layout

- **`mcheli-agnostic/`**: the game logic in plain Java, with no Minecraft, NeoForge or LWJGL on the classpath. The compiler enforces this, so a platform import here fails to build. It contains the config DSL parsers (`*Info` / `*InfoManager`), the `eval` expression engine, model parsing (`.mqo`/`.obj`), flight and drive simulation (`sim/`), weapons, ballistics and missile guidance (`weapon/`), and HUD definitions. JUnit tests live here. Many are tick-by-tick "trace" tests that check behaviour against the original 1.7.10 entity code.
- **`mcheli-agnostic/.../spi/`**: the boundary interfaces (`WorldView`, `EntityRef`, `ResourceSource`, `ModelHandle`, `Network`, `SyncedData`, `RandomSource`, `Logger`, …). Agnostic code talks to the game only through these.
- **`neoforge-1.21.1/`** (package `mcheli.dependent`, entrypoint `mcheli.MCHeli`): the platform layer. `port/` implements the SPI with NeoForge types (`Neo*` classes). It also holds entities, renderers, networking payloads (`control/`), registries, items, the menu, and one client mixin (`CameraMixin`, registered in `mcheli.mixins.json`).
- **`fabric-26.2/`** (package `mcheli.fabric`, entrypoints `MCHeliFabric` / `client.MCHeliFabricClient`): the Fabric 26.2 port. It is currently an **empty skeleton** that only proves the wiring: `mcheli-agnostic` on the classpath and the shared assets bundled. It uses Loom `splitEnvironmentSourceSets()`, so client-only code goes in `src/client/java`, and Loom fails the build if `src/main` references client classes. Versions are pinned in `fabric-26.2/gradle.properties`.
- **`mcheli-assets/`**: MCHeli's own content, shared by every platform module through `sourceSets.main.resources.srcDir`. That covers the vehicle, weapon and HUD `.txt` configs, `.mqo`/`.obj` models, textures, sounds, `sounds.json` and lang. It is a plain folder, not a Gradle project. Vanilla-format JSON whose format changes between MC versions stays in each module's own `src/main/resources`: item models and client items, particle definitions, and recipes.
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

## Commits

Commit regularly at logical checkpoints, after the build or test is verified. Do **not** add `Co-Authored-By` or any other AI attribution to commit messages.

## In-game self-tests

`mcheli.dependent.Demo*SelfTest` classes are game-bus listeners that spawn probe entities and log a pass/fail verdict. They run only in dev (`!FMLEnvironment.production`). Most are commented out in the `MCHeli` constructor; uncomment one to run it. `DemoCrushSelfTest` is gated by the `mcheli.selftests` property instead (`-PmcheliSelfTests`).
