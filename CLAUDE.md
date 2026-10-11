# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MCHeli is EMB4's vehicle mod for Minecraft 1.7.10 (helicopters, planes, tanks, vehicles, weapons). This repo is a fork of the existing **NeoForge 1.21.1** port ([Modrinth: mcheli-modern-port](https://modrinth.com/mod/mcheli-modern-port), upstream source [ThatSoulyGuy/MCHeli](https://github.com/ThatSoulyGuy/MCHeli)).

**The goal of this fork is to port the mod to Fabric for Minecraft 26.2**, which runs on Java 25. That is why the repo is named `MCHeli-26.2-fabric`. The NeoForge module is the starting point and working reference. The original 1.7.10 mod's decompiled source is in `reference/decompiled-srg/mcheli/`.

The NeoForge port's own description gives its starting state (alpha):
- Working: 112 vehicles, flight and drive physics, weapons (heat, rockets, guided missiles with lock-on, bombs, torpedoes, flares), crew and gunner seats with seat switching, per-part damage, the cockpit HUD, free-look, camera cycling, rotor folding, and content packs placed in `.minecraft/mcheli/`.
- Missing: survival recipes (vehicles can only be spawned in creative), fireable weapons on some recon aircraft, UAV drones, and night/thermal vision.

## Dev environment

`devenv.nix` provides JDK 8, 21 and 25 and the native libraries that `runClient` needs on NixOS. Enter it with `devenv shell`. It exports `JDK8`, `JDK21` and `JDK25`, and `gradle.properties` (`org.gradle.java.installations.fromEnv`) points Gradle's toolchain discovery at them. Outside devenv, Gradle falls back to downloading JDKs through foojay. The shell also defines `test-agnostic`, `run-neoforge-client` and `run-neoforge-server` as shortcuts.

## Build & test

The Gradle wrapper is 9.x. Each subproject declares its Java version through toolchains: 21 for the agnostic and NeoForge modules, 8 for the 1.7.10 reference, and 25 for the planned Fabric 26.2 module.

```
./gradlew :mcheli-agnostic:test                                   # unit tests (pure Java, no Minecraft)
./gradlew :mcheli-agnostic:test --tests 'mcheli.agnostic.sim.HeliFlightModelTraceTest'   # single test class
./gradlew :mcheli-agnostic:test --tests '*WeaponAimTest.someMethod'                      # single method
./gradlew :neoforge-1.21.1:build                                  # mod jar (agnostic jar embedded via jarJar)
./gradlew :neoforge-1.21.1:runClient
./gradlew :neoforge-1.21.1:runServer
./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests            # headless in-game self-test, halts after the verdict
```

There is no linter. Do not enable `org.gradle.configuration-cache` globally (see `gradle.properties`).

## Module layout

- **`mcheli-agnostic/`**: the game logic in plain Java, with no Minecraft, NeoForge or LWJGL on the classpath. The compiler enforces this, so a platform import here fails to build. It contains the config DSL parsers (`*Info` / `*InfoManager`), the `eval` expression engine, model parsing (`.mqo`/`.obj`), flight and drive simulation (`sim/`), weapons, ballistics and missile guidance (`weapon/`), and HUD definitions. JUnit tests live here. Many are tick-by-tick "trace" tests that check behaviour against the original 1.7.10 entity code.
- **`mcheli-agnostic/.../spi/`**: the boundary interfaces (`WorldView`, `EntityRef`, `ResourceSource`, `ModelHandle`, `Network`, `SyncedData`, `RandomSource`, `Logger`, …). Agnostic code talks to the game only through these.
- **`neoforge-1.21.1/`** (package `mcheli.dependent`, entrypoint `mcheli.MCHeli`): the platform layer. `port/` implements the SPI with NeoForge types (`Neo*` classes). It also holds entities, renderers, networking payloads (`control/`), registries, items, the menu, and one client mixin (`CameraMixin`, registered in `mcheli.mixins.json`).
- **`mcheli-1.7.10/`**: the decompiled, deobfuscated and runnable original (RetroFuturaGradle, Java 8). It is the **ground truth** for porting behaviour. It is currently commented out in `settings.gradle`; its README explains how it was produced and how to run it. It needs the original `MC1.7.10_mcheli_1.0.3.zip`, which is not committed (`-PmcheliJar=...`).
- **`reference/decompiled-srg/mcheli/`**: the raw CFR decompile of the original mod, with SRG names (`func_*` / `field_*`). It is reference material, not a Gradle project. For readable MCP names, use `mcheli-1.7.10/src/main/java/` instead.

For the Fabric port, the intended split is that `mcheli-agnostic` is reused unchanged and a new Fabric 26.2 module implements the same SPI, the way `neoforge-1.21.1` does. That module has to compile `mcheli-agnostic` for Java 25 or depend on its Java 21 bytecode, either of which works.

Code in the agnostic layer often keeps the original class names (`MCH_WeaponInfo`, `MCH_HeliInfoManager`, `MCP_PlaneInfoManager`) so it can be cross-referenced with the 1.7.10 source. Keep that convention when porting more classes.

## Key wiring (neoforge `MCHeli` constructor → `commonSetup`)

1. `MchContentPacks.discover()` runs first, before the registries freeze. It scans `<gamedir>/mcheli/<pack>/`, where content sits either flat or under `assets/mcheli/`. It builds a `CompositeResourceSource` that combines the bundled assets and the packs.
2. `MchRegistries.registerVehicles()` creates one spawn item per vehicle config found. It must run before `register(modEventBus)`.
3. `MchSounds` registers a `SoundEvent` per pack `.ogg`. `MchPackResourcesFinder` exposes pack textures, a generated `sounds.json` and the `.ogg` files to the vanilla resource manager.
4. In `commonSetup`, every `*InfoManager.load(res, log, "<dir>")` parses configs through that same `ResourceSource`.

Assets travel by two routes: configs and models go through the agnostic `ResourceSource`, while textures, sounds and `sounds.json` go through the vanilla client resource manager. The original mod loaded content from disk with `java.io.File`. The port must load it through the resource system.

Bundled content (vehicle and weapon `.txt` configs, models, textures, sounds) is under `neoforge-1.21.1/src/main/resources/assets/mcheli/`.

## Gradle gotchas (`neoforge-1.21.1/build.gradle`)

- Do not add a `repositories {}` block. Doing so disables the NeoForge repos that ModDevGradle manages; repositories belong in `settings.gradle`.
- The agnostic dependency has to be declared three ways: `implementation` (compile), `additionalRuntimeClasspath` (dev runs, otherwise `ClassNotFoundException`), and `jarJar` (production jar).
- Do not put a `version { strictly ... }` block on the project dependency. It overwrites `project.version`.

## In-game self-tests

`mcheli.dependent.Demo*SelfTest` classes are game-bus listeners that spawn probe entities and log a pass/fail verdict. They run only in dev (`!FMLEnvironment.production`). Most are commented out in the `MCHeli` constructor; uncomment one to run it. `DemoCrushSelfTest` is gated by the `mcheli.selftests` property instead (`-PmcheliSelfTests`).
