# Porting MCHeli to Fabric 26.2

Source: `neoforge-1.21.1/src/main/java`, about 17k lines (`mcheli.MCHeli` plus `mcheli.dependent.*`).
Target: `fabric-26.2/`, currently an empty skeleton that builds and launches.
`mcheli-agnostic` (about 17.6k lines of game logic) and `mcheli-assets/` are reused as they are.

## Documents in this folder

| File | What it answers |
|---|---|
| [`neoforge-to-fabric.md`](neoforge-to-fabric.md) | The Fabric API equivalent of every NeoForge symbol the platform layer uses, or the mixin needed where Fabric has no hook. Its first section lists facts checked against the real 26.2 jars. |
| [`vanilla-1.21.1-to-26.2.md`](vanilla-1.21.1-to-26.2.md) | Vanilla Minecraft API changes between 1.21.1 and 26.2, grouped by area, with the files each one affects. |

## Pinned toolchain (checked 2026-10-11)

| Component | Version | Where |
|---|---|---|
| Minecraft | 26.2 (26.3 is out, but the target is 26.2) | `fabric-26.2/gradle.properties` |
| Fabric Loader | 0.19.5 | same |
| Fabric API | 0.161.0+26.2 | same |
| Loom | 1.18.4, plugin `net.fabricmc.fabric-loom`, no remapping | root `build.gradle` |
| Gradle | 9.7.1 (Loom needs 9.7.0 or newer) | `gradle/wrapper` |
| Java | 25 | toolchain; devenv `JDK25` |

## What will need the most work

Size of each NeoForge package, in lines:

| Package | Lines | Port effort |
|---|---|---|
| `entity/` (`AbstractMchVehicle` alone is 3.5k) | 6.3k | Medium. Mostly vanilla `Entity` API changes: `hurtServer`; save/load through `ValueInput`/`ValueOutput`; `lerpTo` replaced by the final `moveOrInterpolateTo`, which matters for `MchBullet`; and `EntityDataSerializers.COMPOUND_TAG` removed, so the two synced compound fields need a custom serializer. The physics is already in agnostic. |
| `client/` (renderers, input, camera, sound) | 2.8k | **High.** Entity renderers move to 26.x extract/submit (`EntityRenderState` + `SubmitNodeCollector`), and the model renderer moves to new `RenderType`/`RenderPipeline` APIs. |
| `Demo*SelfTest` | 3.6k | Low. Mechanical event rewiring. These are the **acceptance tests** for the port (see below). |
| `port/` (SPI implementations, content packs) | 1.1k | Medium. Pack injection needs a `PackRepository` mixin. |
| `control/` (payloads) | 0.9k | Low. Codecs carry over; only the handler wiring changes. |
| `item/` + `registry/` | 1.0k | Medium. `Item.Properties.setId`, client-item JSON, the recipe API, and a `SpecialModelRenderer` for vehicle icons. |
| `client/hud`, `client/screen`, `client/particle`, `particle/`, `menu/` | 1.3k | **High** (HUD) to medium. GUI extract model (`GuiGraphicsExtractor`) and particle render changes. |
| `mixin/CameraMixin` | 0.1k | Medium. `Camera.setup` no longer exists; retarget it, and absorb the FOV hook (`Camera.calculateFov`). |

## Suggested phase order (not started)

Each phase ends with a check that can be repeated, so a regression shows up in the phase that caused it.

1. **Content and registries (server side).**
   - Port `port/` (`FabricResourceSource` from `NeoResourceSource` via `FabricLoader.getModContainer().findPath`, plus `MchContentPacks`), `registry/` and the `*InfoManager.load` calls.
   - Check: a dev server log shows `Read 33 helicopters / 51 planes / 12 tanks / 16 vehicles / 229 weapons / 19 huds` and the m230/m1a2/model check lines. These are the same numbers the NeoForge build logs today.
2. **Entities, networking, server simulation.**
   - Port `entity/`, `control/`, `item/` and `menu/`, then port the `Demo*SelfTest` classes.
   - Check: the headless self-tests pass on Fabric, starting with `DemoCrushSelfTest` (`RESULT: PASS`). Each NeoForge self-test is a ready-made acceptance test for one feature: heli lift, homing, VTOL, bombs, flares, torpedo, HP, crew/fuel, content packs, CAS, dispenser.
3. **Client rendering.**
   - Port the entity renderers and `MchModelRenderer` to extract/submit, the vehicle item icons (`SpecialModelRenderer` plus generated `items/*.json`), particles, key mappings, input, and the camera and FOV mixins.
   - Check: `runClient`, then spawn one vehicle of each type.
4. **HUD, screens, sound.** Port the HUD to `HudElementRegistry` with `GuiGraphicsExtractor`, the vehicle menu screen, and engine sound.
5. **Content-pack client resources and recipes.** Port the `PackRepository` mixin for pack textures, sounds and `sounds.json`, and the recipe pack.

## Reference sources while porting

- **Behaviour ground truth:** `mcheli-1.7.10/src/main/java` (MCP names) and `reference/decompiled-srg/mcheli/` (raw SRG).
- **Working modern implementation:** `neoforge-1.21.1/` (it builds and its self-test passes on this branch; keep it that way).
- **26.2 vanilla signatures:** `javap -p -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.2/minecraft-common-deobf-26.2.jar:…/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar <class>`. Running `./gradlew :fabric-26.2:genSources` produces readable sources for the IDE.
- **Fabric API source** at the pinned tag: https://github.com/FabricMC/fabric-api/tree/0.161.0+26.2
