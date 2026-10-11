# Porting MCHeli to Fabric 26.2

Source: `neoforge-1.21.1/src/main/java`, about 17k lines (`mcheli.MCHeli` plus `mcheli.dependent.*`).
Target: `fabric-26.2/`. The whole platform layer is ported (status below).
`mcheli-agnostic` (about 17.6k lines of game logic) and `mcheli-assets/` are reused as they are.

## Status (2026-10-11): ported

**Everything compiles and builds.** The production jar (`./gradlew :fabric-26.2:prodServer`) runs on a real Fabric server install.

**Server self-tests** (`-PmcheliSelfTests=<name>`, run one at a time on a fresh world): 15 of 19 pass. These pass: crush, heli, homing, vtol, bomb, torpedo, bullet, particle, hud, heat, hp, crewfuel, contentpack, cas and dispenser. The other four:
- **`tank`, `plane`, `weapon`:** fail identically on the unmodified NeoForge module (`weapon` with an NPE). They are out of date against the current agnostic code and were not broken by the port.
- **`flare`:** fails only on Fabric, and it is a bug in the test that 26.x exposes. Its guided missile has no shooter, so guidance discards it on the first tick. On 1.21.1 the missile sat outside the entity-ticking spawn chunks, never ticked, and the test passed by accident.

**`contentpack` needs packs written before boot.** Create `fabric-26.2/run/mcheli/testpack_rp/assets/mcheli/...` and `fabric-26.2/run/mcheli/testpack_flat/...`, each containing:
- `helicopters/<vehicle>.txt`
- `models/helicopters/<vehicle>.mqo`
- `textures/helicopters/<vehicle>.png`
- `sounds/<sound>.ogg`

The names are `mch_testpack_heli`/`mch_testpack_snd` for `testpack_rp` and `mch_flatpack_heli`/`mch_flatpack_snd` for `testpack_flat`. Copy the files from any bundled helicopter. Add `Bad Name!.txt` and `fuel.txt` to the flat pack as the adversarial files.

**Client gametest** (`./gradlew :fabric-26.2:runClientGameTest`): a real client with one vehicle per category. It checks that:
- the AH-64 cockpit draws the camera mixin and the full config HUD;
- third person works;
- firing reaches the client (rounds and cartridges are counted);
- the riding GUI opens through keybind → payload → menu.

Screenshots go to `fabric-26.2/build/run/clientGameTest/screenshots`.

**Content packs on the client:** both pack layouts mount into the client resource stack, and the generated 26.x `pack.mcmeta` is accepted.

How the NeoForge-only pieces were replaced (details in the two documents below):
- **Event subscribers** became explicit `register()` calls from `mcheli.MCHeli` and `mcheli.MCHeliClient`.
- **Hooks with no Fabric event** became four mixins: `BuiltInPackSourceMixin`, `CameraMixin` (`alignWithEntity` with roll, plus FOV), `MinecraftMixin` (render-frame hook) and `EntityRenderDispatcherMixin` (rider hiding).
- **Entity renderers** keep the entity and partial tick in the render state and queue one custom-geometry node that runs the existing draw code. The removed `entityTranslucentCull` is rebuilt on its pipeline (`MchRenderTypes`).
- **Item icons** are the `mcheli:vehicle` special model renderer plus 26.x client item definitions; content-pack vehicles get theirs synthesized.
- **Synced compound tags** use a custom `MchDataSerializers.COMPOUND_TAG`, because vanilla removed its serializer.

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

## Phase order used

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
