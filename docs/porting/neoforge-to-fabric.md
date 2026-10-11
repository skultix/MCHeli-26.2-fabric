# NeoForge 1.21.1 → Fabric 26.2 mapping for `neoforge-1.21.1/src/main/java`

How this was checked: every Fabric API name below was read from the source at tag `0.161.0+26.2` (it is the same commit as branch `26.2`, 6a8ed6b), https://github.com/FabricMC/fabric-api/tree/0.161.0+26.2. Loom and class tweaker facts come from https://docs.fabricmc.net/develop/loom/ and https://docs.fabricmc.net/develop/class-tweakers/.
Anything marked **unverified** was not checked against 26.2 sources. That covers vanilla 26.2 method names and the Fabric Loader 0.19.5 source.
File paths are relative to `mcheli/dependent/` unless they start with `mcheli/`.

## Checked against the real 26.2 jars (2026-10-11, `javap` on Loom's `minecraft-{common,clientonly}-deobf-26.2.jar`)

These results **override** any conflicting "unverified" note in the table below.

| Claim in the table | 26.2 reality |
|---|---|
| FOV hook: mixin on `GameRenderer.getFov(...)` | **Wrong.** `GameRenderer` has no FOV method anymore. FOV now lives on `Camera`: `private float calculateFov(float)`, `public float getFov()`, plus `tickFov()` and `modifyFovBasedOnDeathOrFluid(float, float)`. Hook `Camera.calculateFov` instead, which can be done in the existing `CameraMixin`. |
| `CameraMixin` injects into `Camera.setup` (TAIL) | **`Camera.setup` no longer exists.** Candidates are `public void update(DeltaTracker)`, `private void alignWithEntity(float)`, `protected setPosition(...)`/`setRotation(float, float)`/`move(...)`, and `extractRenderState(CameraRenderState, float)`. The mixin must be retargeted. |
| `Camera.getMaxZoom(float)` (unverified) | **Confirmed**: `private float getMaxZoom(float)`, reachable with `@Shadow`. |
| `EnderDragonPart.parentMob` (unverified) | **Confirmed**: `public final EnderDragon parentMob` in package `net.minecraft.world.entity.boss.enderdragon`. |
| `KeyMapping.Category.register(Identifier)` (unverified) | **Confirmed** public static. |
| Mob-griefing game rule accessor (unverified) | Rule constant is `net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING` (`GameRule<Boolean>`). |
| `hurt` → `hurtServer` | **Confirmed**: `Entity.hurt(DamageSource, float)` is `final`. Override `hurtServer(ServerLevel, DamageSource, float)` and `hurtClient(DamageSource)` instead. |
| `BlockEntityWithoutLevelRenderer` removed | **Confirmed** (class absent). `SpecialModelRenderers.ID_MAPPER` exists; it is private in vanilla and opened by Fabric's transitive class tweaker. |
| Frame-rotation hook in `Minecraft.runTick` | `private void runTick(boolean)` exists, and so does `Minecraft.getDeltaTracker()`. |
| Rider-hide hook | `EntityRenderDispatcher.shouldRender(E, Frustum, double, double, double)`, `extractEntity(E, float)` and `submit(S, CameraRenderState, double, double, double, PoseStack, SubmitNodeCollector)` all exist. |
| Loom dev runtime for `mcheli-agnostic` (unverified) | **Confirmed**: plain `implementation` is enough. The skeleton's `runServer` and `runClient` both executed agnostic code. |
| Graphics backend | 26.2 probes a Vulkan loader first and falls back to OpenGL (devenv provides `vulkan-loader`). On the dev machine it picked OpenGL, so renderers must not assume a GL-only path. |

Points that hold across the whole table:
- Fabric API 26.x uses Mojang names: `Identifier` (not `ResourceLocation`), `KeyMappingHelper`, `ParticleProviderRegistry`, `LevelRenderEvents`, `CreativeModeTabEvents`, `ExtendedMenuType`, and `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`. The older `playC2S()/playS2C()`, `KeyBindingHelper`, `ParticleFactoryRegistry`, `WorldRenderEvents`, `ItemGroupEvents` and `ExtendedScreenHandlerType` no longer exist in 26.2.
- Fabric has no event bus or `@SubscribeEvent`. Each listener becomes a static `Event.register(lambda)` call made from an entrypoint.
- GUI and entity rendering in 26.x is split into extract and submit phases. `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` replaces the old `render(GuiGraphics, …)`. The HUD and renderer code therefore needs rewriting for vanilla reasons, independent of the Fabric API.

## Symbol table

| NeoForge | Fabric 26.2 equivalent | Files affected | Notes / risk |
|---|---|---|---|
| `@Mod`, `ModContainer`, `IEventBus` (mod bus), constructor injection | `ModInitializer.onInitialize()` (`main` entrypoint) and `ClientModInitializer.onInitializeClient()` (`client` entrypoint), declared in `fabric.mod.json` | `mcheli/MCHeli.java`, `registry/MchRegistries.java`, `registry/MchSounds.java` | Low. The constructor body moves to `onInitialize`. |
| `FMLCommonSetupEvent` | Run the code at the end of `onInitialize()`. Registries are still open at that point, and nothing loads in parallel. | `mcheli/MCHeli.java` | Low. Config `*InfoManager.load` can run in `onInitialize` right after `MchContentPacks.discover()`. |
| `DeferredRegister`, `DeferredRegister.Items`, `DeferredItem`, `.register(modBus)` | `Registry.register(BuiltInRegistries.X, ResourceKey/Identifier, obj)` called eagerly in `onInitialize`. Static `Supplier` fields become plain fields or `Holder`s. | `registry/MchRegistries.java`, `registry/MchSounds.java`, `mcheli/MCHeli.java` | Medium, mostly vanilla churn: items need `Item.Properties.setId(ResourceKey)` and `EntityType.Builder.build(ResourceKey)` (vanilla since 1.21.2). Every `.get()` call site changes. |
| Creative tabs registered through `DeferredRegister<CreativeModeTab>` | `FabricCreativeModeTab.builder()`, then `Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, …)`. `CreativeModeTabEvents` for adding to existing tabs. | `registry/MchRegistries.java` | Low. `fabric-creative-tab-api-v1` (`api/creativetab/v1/FabricCreativeModeTab.java`). |
| `IMenuTypeExtension.create(...)` (inline FQN) | `new ExtendedMenuType<>(factory, StreamCodec)` from `fabric-menu-api-v1` (`api/menu/v1/ExtendedMenuType.java`). The server side opens it through an `ExtendedMenuProvider`. | `registry/MchRegistries.java`, `control/ServerboundVehicleGuiPayload.java` (`sp.openMenu`) | Medium. The extra data is now a typed `D` with a `StreamCodec`, replacing a raw buffer writer. |
| `RegisterMenuScreensEvent` (inline FQN) | Vanilla `MenuScreens.register(type, ctor)`, called from `onInitializeClient` | `client/MchClientEvents.java` | Low. `fabric-transitive-access-wideners-v1` makes `MenuScreens.register` and `ScreenConstructor` accessible. |
| `@SubscribeEvent`, `@EventBusSubscriber(value=Dist.CLIENT)` | Explicit `XEvents.EVENT.register(...)` calls in `onInitializeClient`, or in `onInitialize` for common code | `client/MchClientEvents.java`, `MchClientInput.java`, `MchClientRotation.java`, `MchClientSound.java`, `MchGunnerView.java`, `MchKeybinds.java`, `MchRiderRenderHider.java`, `client/hud/MchHudOverlay.java`, `client/hud/MchWrenchOverlay.java`, all `Demo*SelfTest.java` (28 files) | Low. Mechanical. |
| `NeoForge.EVENT_BUS.register(new DemoXSelfTest())` | The self-test registers its own `ServerLifecycleEvents`/`ServerTickEvents` lambdas, e.g. a `register()` method | `mcheli/MCHeli.java`, `Demo*SelfTest.java` | Low |
| `ServerStartedEvent` | `ServerLifecycleEvents.SERVER_STARTED.register(server -> …)` (`fabric-lifecycle-events-v1`) | 19 `Demo*SelfTest.java` | Low |
| `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK.register(server -> …)`. Per-level variants: `START_LEVEL_TICK`/`END_LEVEL_TICK` (`ServerLevel`). | 15 `Demo*SelfTest.java` | Low. In 26.2 the per-level events are named `*_LEVEL_TICK` (formerly `*_WORLD_TICK`). |
| `ClientTickEvent.Post` | `ClientTickEvents.END_CLIENT_TICK.register(mc -> …)` (`api/client/event/lifecycle/v1/ClientTickEvents.java`) | `client/MchClientInput.java`, `MchClientRotation.java`, `MchClientSound.java`, `MchGunnerView.java`, `MchKeybinds.java` | Low |
| `Dist`, `@OnlyIn(Dist.CLIENT)`, `FMLEnvironment.dist` | `FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT`, `@Environment(EnvType.CLIENT)` (`net.fabricmc.api`). The better option is Loom `splitEnvironmentSourceSets()` with client code under `src/client/java`. | `client/particle/*Particle.java` (3, `@OnlyIn`), `control/ClientboundRwrPayload.java` (`FMLEnvironment.dist != Dist.CLIENT`), plus the 13 `Dist` imports used by the subscribers | Low. The RWR clientbound handler moves to a client-only `ClientPlayNetworking.registerGlobalReceiver`, which removes the dist check. |
| `FMLEnvironment.production` | `!FabricLoader.getInstance().isDevelopmentEnvironment()` | `mcheli/MCHeli.java` | Low. Loader API (**unverified** against 0.19.5 source, but this API has been stable for years). |
| `FMLPaths.GAMEDIR.get()` | `FabricLoader.getInstance().getGameDir()` | `port/MchContentPacks.java` | Low |
| `ModList.get().getModFileById(id).getFile().findResource(...)` | `FabricLoader.getInstance().getModContainer("mcheli").orElseThrow().findPath("assets/mcheli/" + dir)` returns `Optional<Path>`. `getRootPaths()` is also available. | `port/NeoResourceSource.java` | Low. Paths are zipfs in production and directories in dev, which matches the NeoForge behaviour. |
| `RegisterPayloadHandlersEvent`, `PayloadRegistrar` (`playToServer`/`playToClient`) | `PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC)` / `PayloadTypeRegistry.clientboundPlay().register(...)` in `onInitialize` (both sides). Handlers: `ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> …)` in common code, `ClientPlayNetworking.registerGlobalReceiver` in client code. | `control/MchControlNetwork.java`, `control/package-info.java` | Low. Payload records, `CustomPacketPayload.Type` and `StreamCodec` stay as they are. The codec parameter is `StreamCodec<? super RegistryFriendlyByteBuf, T>`. |
| `IPayloadContext` (`ctx.player()`, `ctx.enqueueWork`) | `ServerPlayNetworking.Context` (`player()`, `server()`, `responseSender()`). On the client, `ClientPlayNetworking.Context`. | 11 `control/*Payload.java` | Low. Fabric runs handlers **on the server or render thread** already (per the javadoc), so `enqueueWork` wrappers can be dropped. |
| `PacketDistributor.sendToServer(p)` | `ClientPlayNetworking.send(p)` | `client/MchKeybinds.java`, `MchClientInput.java`, `MchClientRotation.java`, `client/screen/MchVehicleScreen.java` | Low |
| `PacketDistributor.sendToPlayer(sp, p)` (inline FQN) | `ServerPlayNetworking.send(sp, p)`. For broadcasts, use `PlayerLookup.tracking(entity)` and loop. | `entity/AbstractMchVehicle.java:1641` | Low |
| `PartEntity<?>` (`instanceof`, `getParent()`) | No Fabric multipart API exists. The vanilla analogue is `EnderDragonPart` (`parentMob`). MCHeli never **creates** parts; it only unwraps other mods' parts. | `entity/MchBullet.java`, `entity/MchExplosion.java`, `item/MchWrench.java` | Low. Replace with `instanceof EnderDragonPart p ? p.parentMob : e`, or drop the check. (**unverified** that the 26.2 field is still `parentMob`.) |
| `FakePlayer`, `FakePlayerFactory.getMinecraft(sl)` (inline FQN) | `net.fabricmc.fabric.api.entity.FakePlayer.get(ServerLevel)` (`fabric-events-interaction-v0`) | `entity/MchBullet.java` (dispenser weapon), `DemoCrewFuelSelfTest.java` | Low |
| `EventHooks.canEntityGrief(level, this)` | Vanilla mob-griefing game rule read from `ServerLevel` game rules | `entity/AbstractMchVehicle.java:1477` | Low. The 26.x game-rule accessor changed (game rules became registry-based in 1.21.11), so the exact call is **unverified**. |
| `Tags.Blocks.GLASS_BLOCKS / GLASS_PANES / FENCES_NETHER_BRICK` | `ConventionalBlockTags.GLASS_BLOCKS`, `.GLASS_PANES`, `.NETHER_BRICK_FENCES` (`fabric-convention-tags-v2`, `c:` namespace) | `entity/MchCrushableBlocks.java` | Low. Both sides use the `c:` namespace, so the same tags resolve. `FluidTags`/`BlockTags` elsewhere are vanilla. |
| `AddPackFindersEvent` + `event.addRepositorySource(...)` (client resources for content packs and a server-data recipe pack) | **No public Fabric hook for a custom `RepositorySource`/`PackResources`.** `ResourceLoader.registerBuiltinPack(id, container, activation)` only serves `resourcepacks/<id>` folders **inside the mod jar**. | `port/MchContentPacks.java`, `port/MchPackResourcesFinder.java` (`MchPackResources`, `MchRecipePack` are vanilla-only and can be kept) | **High, needs a mixin.** Inject at `PackRepository.<init>` RETURN and add the source to `sources` (Fabric does exactly this in `fabric-resource-loader-v1/.../mixin/resource/PackRepositoryMixin.java`: it makes `sources` `@Mutable`, wraps it in a `LinkedHashSet`, and tells client/server apart by inspecting `FolderRepositorySource.packSource`). Alternatives: mixin `ClientPackSource`/`ServerPacksSource`, or write the recipes as a datapack JSON in the jar (`data/mcheli/recipe/*`) and register the client pack via `ResourceLoader`. **Note:** Fabric's own class tweaker opens `PackRepository.sources` as `accessible` (not transitive), so MCHeli needs its own `@Shadow @Mutable` or class tweaker entry. |
| `RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CROSSHAIR, id, layer)` | `HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id, element)` (`fabric-rendering-v1`, `api/client/rendering/v1/hud/`) | `client/hud/MchHudOverlay.java`, `client/hud/MchWrenchOverlay.java` | **Medium.** `HudElement` is `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`, the 26.x extract model. The HUD draw adapter (`fill`/`line`/`texture`/`string`) must target `GuiGraphicsExtractor`. Custom line drawing with `line(...)` and `lineWidth` may need a custom `RenderPipeline` or the `PictureInPictureRendererRegistry` (**unverified**). |
| `RenderGuiLayerEvent.Pre` (cancels the vanilla crosshair when it is the HUD) | `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> { if (!hide) orig.extractRenderState(g, dt); })` | `client/hud/MchHudOverlay.java` | Low |
| `RegisterKeyMappingsEvent`, `KeyConflictContext.IN_GAME` | `KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcheli.x", InputConstants.Type.KEYSYM, key, category))` (`fabric-key-mapping-api-v1`, `api/client/keymapping/v1/`). The category is a vanilla `KeyMapping.Category` record (create one with `KeyMapping.Category.register(Identifier)`, **unverified** exact factory name). | `client/MchKeybinds.java` | Low/Medium. There is no conflict context; gate on `mc.screen == null` in the tick handler. |
| `RegisterParticleProvidersEvent.registerSpriteSet(type, Provider::new)` | `ParticleProviderRegistry.getInstance().register(type, Provider::new)` (the `PendingParticleProvider<T>` overload takes a `FabricSpriteSet`) | `client/MchClientEvents.java`, `client/particle/*` | Low. 26.x particles also moved to extract/submit-style rendering, so the `Particle` subclasses (custom `getRenderType`) likely need vanilla rework. |
| `EntityRenderersEvent.RegisterRenderers.registerEntityRenderer` | `EntityRendererRegistry.register(type, Renderer::new)` (`fabric-rendering-v1`) | `client/MchClientEvents.java` | Low for registration. **High** for the renderers themselves, because 26.x uses the `EntityRenderState` + `SubmitNodeCollector` pipeline. That is vanilla churn. |
| `RegisterClientExtensionsEvent` + `IClientItemExtensions.getCustomRenderer()` returning a `BlockEntityWithoutLevelRenderer` | `BEWLR` no longer exists in vanilla (removed in 1.21.4). Use a vanilla `SpecialModelRenderer` registered through `SpecialModelRenderers.ID_MAPPER.put(Identifier, MapCodec)` (opened by `fabric-transitive-access-wideners-v1`), plus an `items/<name>.json` client item using `"type":"minecraft:special"`. | `client/MchClientEvents.java`, `client/VehicleItemRenderer.java`, item model JSONs | **High.** It needs a rewrite and one generated client-item JSON per vehicle item. Pack vehicles need generated JSON served from `MchPackResources`. |
| `ViewportEvent.ComputeFov` (`setFOV(getFOV()/zoom)`) | No Fabric event. Use a mixin `@ModifyReturnValue`/`@Inject(RETURN)` on `GameRenderer.getFov(...)`. | `client/MchGunnerView.java` | Medium. Mixin; the 26.2 signature is **unverified**. |
| `RenderLivingEvent.Pre` (cancel = hide the rider) | No cancellable Fabric event (`LivingEntityFeatureRenderEvents` only has `ALLOW_CAPE_RENDER`). Use a mixin: HEAD-cancel `EntityRenderDispatcher.shouldRender(...)`, or `LivingEntityRenderer` extract/submit, when `entity.getVehicle()` is an `AbstractMchVehicle` with `hidesRider()`. | `client/MchRiderRenderHider.java` | Medium. Mixin; target method **unverified**. |
| `RenderFrameEvent.Pre` (per-frame rotation after mouse turn; `getPartialTick()`) | No Fabric per-frame pre-render event. `LevelRenderEvents.START_MAIN` fires after camera setup, which is too late for the camera. Use a mixin in `Minecraft.runTick` before `GameRenderer.render(...)`, or at `MouseHandler.handleAccumulatedMovement` TAIL. Get partial tick from `mc.getDeltaTracker()`. | `client/MchClientRotation.java` | Medium. Mixin; injection target **unverified**. |
| `InputEvent.InteractionKeyMappingTriggered` (`isAttack`, `setSwingHand(false)`, cancel) | `ClientPreAttackCallback.EVENT.register((mc, player, clickCount) -> ridingMchVehicle())` (`fabric-events-interaction-v0`, client). Returning true cancels block break, attack **and swing**. | `client/MchClientInput.java` | Low. It fires every tick while held, not once per click; use `clickCount != 0` for click semantics. |
| `PlayerInteractEvent.LeftClickBlock` (cancel) | `AttackBlockCallback.EVENT` returning `InteractionResult.FAIL` (fires on both sides), or handled by `ClientPreAttackCallback` above | `client/MchClientInput.java` | Low |
| `ClientHooks.getDetachedCameraDistance(...)` (inline FQN inside the mixin) | Vanilla `Camera.getMaxZoom(float)` (private; call through `@Shadow` in the same `@Mixin(Camera.class)`) | `mixin/CameraMixin.java` | Medium. The method name and signature in 26.2 are **unverified**. `Camera.setup` may also have changed with the extract refactor. |
| `NeoForge.EVENT_BUS` / `IEventBus` for sounds | `Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))` | `registry/MchSounds.java` | Low |

**NeoForge extension methods.** I grepped every `@Override`. None of `getPickedResult`, `canRiderInteract`, `shouldRiderSit`, `getPassengersRidingOffset`, `sendPairingData`, `isMultipartEntity`/`getParts`, `IEntityWithComplexSpawn` or `IItemExtension` hooks is overridden or called. The only extension-interface uses are `IClientItemExtensions.getCustomRenderer` and `IMenuTypeExtension.create`, both in the table above. All other overrides are vanilla: `getPassengerAttachmentPoint`, `getDismountLocationForPassenger`, `canAddPassenger`, `addPassenger`, `interact`/`interactAt`, `hurt`, `lerpTo`, `isPickable`, `onUseTick`, `getUseDuration`, `canCraftInDimensions` and others. Several of these have changed signatures between 1.21.1 and 26.x (for example `hurt` → `hurtServer`, the recipe API, and `canCraftInDimensions` being removed). That is vanilla migration work, not a Fabric concern.

## Entrypoints

- `mcheli.fabric.MCHeliFabric implements ModInitializer` (already exists as a skeleton) contains:
  1. `MchContentPacks.discover()`
  2. `MchRegistries.registerVehicles()` and the `Registry.register` calls
  3. sounds
  4. `PayloadTypeRegistry.*Play().register` for **both** directions, then `ServerPlayNetworking.registerGlobalReceiver` for the 10 serverbound payloads
  5. `*InfoManager.load(...)`
  6. self-tests when `isDevelopmentEnvironment()`, and the `mcheli.selftests` property for the crush test
- `mcheli.fabric.client.MCHeliFabricClient implements ClientModInitializer` (skeleton, in `src/client/java`) contains:
  - entity renderers, particles, `MenuScreens.register`, key mappings, the HUD elements, tick listeners and `ClientPreAttackCallback`
  - `ClientPlayNetworking.registerGlobalReceiver(ClientboundRwrPayload.TYPE, …)`
- The `PackRepository` mixin must be registered before the first `PackRepository` is constructed. Mixins apply at class load, so this is automatic. The `MchContentPacks.mounts()` data must already be available by then: the client builds its `PackRepository` in the `Minecraft` constructor, which runs **after** `onInitializeClient`. Make `discover()` idempotent or lazy. (Order is **unverified** for 26.2.)

## fabric.mod.json (replaces META-INF/neoforge.mods.toml)

```json
{
  "schemaVersion": 1,
  "id": "mcheli",
  "version": "${version}",
  "name": "MCHeli",
  "description": "MCHeli ported to Fabric 26.2. Original mod by Minato Tanaka (EMB4).",
  "license": "All Rights Reserved",
  "environment": "*",
  "entrypoints": {
    "main":   ["mcheli.fabric.MCHeliFabric"],
    "client": ["mcheli.fabric.client.MCHeliFabricClient"]
  },
  "mixins": [ { "config": "mcheli.mixins.json", "environment": "client" } ],
  "accessWidener": "mcheli.classtweaker",
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

`modLoader`/`loaderVersion` and `[[dependencies]]` blocks are replaced by `depends`. `side="BOTH"` corresponds to `"environment": "*"`. The current `mcheli.mixins.json` has a single `client` entry (`CameraMixin`), which carries over. Set `"compatibilityLevel": "JAVA_25"`, as all 69 Fabric API mixin configs at 0.161.0+26.2 do. New mixins:
- client: `GameRendererMixin` (FOV), the rider-hide mixin, the frame-rotation mixin
- common: `PackRepositoryMixin`, used by both the client resource packs and the server recipe pack

## Mixins / class tweakers (access wideners)

- Access wideners have been renamed **class tweakers**: https://docs.fabricmc.net/develop/class-tweakers/
  - The file starts with `classTweaker	v1	official`. 26.x is unobfuscated, so the namespace is `official`.
  - It is still referenced from `fabric.mod.json` with the **`"accessWidener"`** key.
  - In Loom it is set with **`loom { accessWidenerPath = file("src/main/resources/mcheli.classtweaker") }`**. Fabric API itself does this: `build.gradle` sets `accessWidenerPath = file("gradle/javadoc.classtweaker")`, and `fabric-resource-loader-v1/fabric.mod.json` sets `"accessWidener": "fabric-resource-loader-v1.classtweaker"`.
- There is no `accesstransformer.cfg` in the NeoForge module, so nothing needs translating.
- `fabric-transitive-access-wideners-v1` already opens `MenuScreens.register` and `SpecialModelRenderers.ID_MAPPER`.
- Probably needed:
  - `PackRepository.sources` (accessible + mutable). Alternatively use `@Shadow @Final @Mutable` as Fabric does.
  - `Camera.getMaxZoom`, if it is private and not reached through `@Shadow` (**unverified**).
- Places with no Fabric hook, which need a mixin:
  - pack repository source
  - FOV
  - rider-render cancel
  - per-frame pre-render
  - camera (already a mixin)

## Gradle / Loom: jar-in-jar and the plain `mcheli-agnostic` library

- Plugin `id "net.fabricmc.fabric-loom" version "1.18.4"`. This is the non-remapping plugin for unobfuscated 26.x. `net.fabricmc.fabric-loom-remap` is for ≤1.21.11, per the Loom docs. Fabric API's own build uses `net.fabricmc.fabric-loom` with `options.release = 25`.
- Dependencies use plain configurations, because there is nothing to remap: `minecraft "com.mojang:minecraft:26.2"`, `implementation "net.fabricmc:fabric-loader:0.19.5"`, `implementation "net.fabricmc.fabric-api:fabric-api:0.161.0+26.2"`. `modImplementation` is a remap-plugin concept. That I am **unverified** whether it still exists in 1.18.4, and it is not needed.
- No `mappings` line, since 26.x ships Mojang names.
- **Jar-in-jar:** `include(project(":mcheli-agnostic"))`. Loom docs say `include` "declares a dependency that should be included as a jar-in-jar in the final mod output". It is **not transitive**. For non-mod jars, Loom auto-generates a wrapper `fabric.mod.json`. This replaces NeoForge `jarJar`.
- **Compile + dev runtime:** `implementation project(":mcheli-agnostic")` is enough. Loom's run configs launch from Gradle's `runtimeClasspath`. With no remapping, nothing filters plain jars or directories off it, and Fabric Loader (Knot) treats classpath entries without `fabric.mod.json` as plain libraries. There is no equivalent of NeoForge's `additionalRuntimeClasspath`. (**unverified** for Loom 1.18.4: the docs page does not state it. If a `ClassNotFoundException` appears in `runClient`, add `localRuntime project(":mcheli-agnostic")`.)
- If `mcheli-agnostic` were itself a Loom project, you would depend on its `namedElements` configuration per the docs. It is a plain `java-library`, so the default configuration is correct.
- Java: agnostic builds Java 21 bytecode, which runs on Java 25. The Fabric module uses toolchain 25.
- Optional: `loom { splitEnvironmentSourceSets(); mods { mcheli { sourceSet sourceSets.main; sourceSet sourceSets.client } } }` enforces the client/server split at compile time, replacing `@OnlyIn`.

## Highest-risk items (summary)

1. Item icons, because `BEWLR` was removed: needs a `SpecialModelRenderer` plus a generated client-item JSON per vehicle, including content-pack vehicles.
2. Content-pack and recipe pack injection: needs a `PackRepository` mixin, since there is no Fabric API for this.
3. The 26.x extract/submit rendering (HUD `GuiGraphicsExtractor`, entity render states, particles). This is vanilla churn, but it is most of the work.
4. Four new client mixins (FOV, rider hide, per-frame rotation, and the camera `getMaxZoom` change). Their targets are unverified against 26.2.
