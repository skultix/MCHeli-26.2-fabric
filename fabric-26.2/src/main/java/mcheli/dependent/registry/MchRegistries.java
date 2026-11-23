package mcheli.dependent.registry;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import mcheli.MCHeli;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchCartridge;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.particle.MuzzleFxOptions;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Central registration for the FULL vehicle fleet. There are exactly FOUR vehicle {@link EntityType}s — one per
 * category (helicopter/plane/tank/vehicle) — each serving every config in that category; the specific vehicle is
 * carried by the entity's synced config name. Every bundled config becomes its OWN spawn {@link VehicleSpawnItem}
 * (scanned from the resources at init), listed under its category's creative tab with its own name + 3D-model icon.
 * This mirrors 1.7.10 MCHeli (4 entity classes + one item per vehicle).
 *
 * <p>Fabric port: registration is EAGER ({@link Registry#register}) from class init, which {@link MCHeli#onInitialize}
 * triggers while the registries are still open. {@link MchHolder} keeps the NeoForge-era {@code .get()} call sites.
 */
public final class MchRegistries {
    private MchRegistries() {}
    private static final Logger LOG = LogUtils.getLogger();

    /** A vehicle category: its resource directory + the entity type that serves it. */
    public enum Category {
        HELICOPTER("helicopters"), PLANE("planes"), TANK("tanks"), VEHICLE("vehicles");
        public final String dir;
        Category(String dir) { this.dir = dir; }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MCHeli.MODID, path);
    }

    private static <T, V extends T> MchHolder<V> register(Registry<T> registry, String name, V value) {
        Identifier id = id(name);
        Registry.register(registry, id, value);
        return new MchHolder<>(id, value);
    }

    private static <T extends net.minecraft.world.entity.Entity> MchHolder<EntityType<T>> entity(String name,
            EntityType.Builder<T> builder) {
        return register(BuiltInRegistries.ENTITY_TYPE, name,
            builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id(name))));
    }

    /** Item ids live in a shared namespace; this tracks every id taken so pack configs can skip collisions. */
    private static final java.util.Set<String> ITEM_IDS = new java.util.HashSet<>();

    private static <T extends Item> MchHolder<T> item(String name, Function<Item.Properties, T> factory,
            Item.Properties props) {
        ITEM_IDS.add(name);
        return register(BuiltInRegistries.ITEM, name,
            factory.apply(props.setId(ResourceKey.create(Registries.ITEM, id(name)))));
    }

    private static <T extends net.minecraft.core.particles.ParticleOptions> MchHolder<ParticleType<T>> particle(
            String name, MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return register(BuiltInRegistries.PARTICLE_TYPE, name, new ParticleType<T>(false) {
            @Override public MapCodec<T> codec() { return codec; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() { return streamCodec; }
        });
    }

    // ---- one EntityType per category (faithful uniform 2.0x0.7 collision core; real hits use the per-part armor
    //      boxes, and getBoundingBoxForCulling() inflates so the big models don't cull). ----
    public static final MchHolder<EntityType<MchHelicopter>> HELI = entity("helicopter",
        EntityType.Builder.<MchHelicopter>of(MchHelicopter::new, MobCategory.MISC).sized(2.0F, 0.7F).clientTrackingRange(10));
    public static final MchHolder<EntityType<MchPlane>> PLANE = entity("plane",
        EntityType.Builder.<MchPlane>of(MchPlane::new, MobCategory.MISC).sized(2.0F, 0.7F).clientTrackingRange(10));
    public static final MchHolder<EntityType<MchTank>> TANK = entity("tank",
        EntityType.Builder.<MchTank>of(MchTank::new, MobCategory.MISC).sized(2.0F, 0.7F).clientTrackingRange(10));
    public static final MchHolder<EntityType<MchGroundVehicle>> VEHICLE = entity("vehicle",
        EntityType.Builder.<MchGroundVehicle>of(MchGroundVehicle::new, MobCategory.MISC).sized(2.0F, 0.7F).clientTrackingRange(10));

    /** The entity type that serves a category. */
    public static EntityType<? extends AbstractMchVehicle> entityTypeFor(Category cat) {
        return switch (cat) {
            case HELICOPTER -> HELI.get();
            case PLANE -> PLANE.get();
            case TANK -> TANK.get();
            case VEHICLE -> VEHICLE.get();
        };
    }

    /** The parsed config for a vehicle (from the category's manager); null before configs load or for an unknown name. */
    public static MCH_AircraftInfo infoFor(Category cat, String name) {
        return switch (cat) {
            case HELICOPTER -> MCH_HeliInfoManager.get(name);
            case PLANE -> MCP_PlaneInfoManager.get(name);
            case TANK -> MCH_TankInfoManager.get(name);
            case VEHICLE -> MCH_VehicleInfoManager.get(name);
        };
    }

    // Projectile + cartridge + particle (NOT per-vehicle).
    public static final MchHolder<EntityType<MchBullet>> DEMO_BULLET = entity("demo_bullet",
        EntityType.Builder.<MchBullet>of(MchBullet::new, MobCategory.MISC).sized(0.2F, 0.2F).clientTrackingRange(6).updateInterval(1));

    public static final MchHolder<EntityType<MchCartridge>> CARTRIDGE = entity("cartridge",
        EntityType.Builder.<MchCartridge>of(MchCartridge::new, MobCategory.MISC)
            .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(3).noSave().noSummon());

    /** A flare / countermeasure decoy — cosmetic (server-authoritative), spawned in volleys by the dispenser. */
    public static final MchHolder<EntityType<mcheli.dependent.entity.MchFlare>> FLARE = entity("flare",
        EntityType.Builder.<mcheli.dependent.entity.MchFlare>of(mcheli.dependent.entity.MchFlare::new, MobCategory.MISC)
            .sized(0.2F, 0.2F).clientTrackingRange(6).updateInterval(1).noSave().noSummon());

    /** The cargo container — a placeable 54-slot storage entity (reference {@code MCH_EntityContainer}). */
    public static final MchHolder<EntityType<mcheli.dependent.entity.MchContainer>> CONTAINER = entity("container",
        EntityType.Builder.<mcheli.dependent.entity.MchContainer>of(mcheli.dependent.entity.MchContainer::new, MobCategory.MISC)
            .sized(2.0F, 1.0F).clientTrackingRange(8));

    public static final MchHolder<ParticleType<MuzzleFxOptions>> WEAPON_FX =
        particle("weapon_fx", MuzzleFxOptions.CODEC, MuzzleFxOptions.STREAM_CODEC);

    /** The big config-scaled explosion fireball (port of {@code MCH_EntityParticleExplode}). */
    public static final MchHolder<ParticleType<mcheli.dependent.particle.MchExplodeOptions>> EXPLODE_FX =
        particle("explode_fx", mcheli.dependent.particle.MchExplodeOptions.CODEC, mcheli.dependent.particle.MchExplodeOptions.STREAM_CODEC);

    /** The soft grey→white smoke billboard (port of {@code MCH_EntityParticleSmoke}) — rotor down-wash + damage smoke. */
    public static final MchHolder<ParticleType<mcheli.dependent.particle.MchSmokeOptions>> SMOKE_FX =
        particle("smoke_fx", mcheli.dependent.particle.MchSmokeOptions.CODEC, mcheli.dependent.particle.MchSmokeOptions.STREAM_CODEC);

    /** The fuel canister — put it in a vehicle's fuel slot (the riding GUI) and the vehicle siphons it into its tank. */
    public static final MchHolder<mcheli.dependent.item.MchFuelItem> FUEL =
        item("fuel", mcheli.dependent.item.MchFuelItem::new, new Item.Properties());

    /** The maintenance wrench — hold right-click on a damaged vehicle to mend it (reference {@code MCH_ItemWrench}). */
    public static final MchHolder<mcheli.dependent.item.MchWrench> WRENCH =
        item("wrench", mcheli.dependent.item.MchWrench::new, new Item.Properties().durability(250));

    /** Places a {@link mcheli.dependent.entity.MchContainer} cargo box (reference {@code MCH_ItemContainer}). */
    public static final MchHolder<mcheli.dependent.item.MchContainerItem> CONTAINER_ITEM =
        item("container", mcheli.dependent.item.MchContainerItem::new, new Item.Properties());

    /** The vehicle ammo items, one per kind of ammunition ({@link mcheli.agnostic.weapon.MCH_AmmoClass}). */
    public static final MchHolder<Item> AMMO_BULLET = item("ammo_bullet", Item::new, new Item.Properties());
    public static final MchHolder<Item> AMMO_AUTOCANNON = item("ammo_autocannon", Item::new, new Item.Properties());
    public static final MchHolder<Item> AMMO_GRENADE = item("ammo_grenade", Item::new, new Item.Properties());
    public static final MchHolder<Item> AMMO_TANK_SHELL = item("ammo_tank_shell", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_ARTILLERY_SHELL = item("ammo_artillery_shell", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_MORTAR = item("ammo_mortar", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_MISSILE = item("ammo_missile", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_ROCKET = item("ammo_rocket", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_BOMB = item("ammo_bomb", Item::new, new Item.Properties().stacksTo(16));
    public static final MchHolder<Item> AMMO_TORPEDO = item("ammo_torpedo", Item::new, new Item.Properties().stacksTo(4));
    public static final List<MchHolder<Item>> AMMO = List.of(AMMO_BULLET, AMMO_AUTOCANNON, AMMO_GRENADE, AMMO_TANK_SHELL,
        AMMO_ARTILLERY_SHELL, AMMO_MORTAR, AMMO_MISSILE, AMMO_ROCKET, AMMO_BOMB, AMMO_TORPEDO);

    /** Refill a used fuel can by crafting it with coal (reference {@code MCH_RecipeFuel}). */
    public static final MchHolder<net.minecraft.world.item.crafting.RecipeSerializer<mcheli.dependent.item.MchFuelRefillRecipe>> FUEL_REFILL =
        register(BuiltInRegistries.RECIPE_SERIALIZER, "fuel_refill", mcheli.dependent.item.MchFuelRefillRecipe.SERIALIZER);

    /** The riding GUI's menu (fuel slots + reload). Opening data = the vehicle's entity id. */
    public static final MchHolder<ExtendedMenuType<mcheli.dependent.menu.MchVehicleMenu, Integer>> VEHICLE_MENU =
        register(BuiltInRegistries.MENU, "vehicle",
            new ExtendedMenuType<>(mcheli.dependent.menu.MchVehicleMenu::new, ByteBufCodecs.VAR_INT));

    // Flat "original" 2D icons for the TAB BUTTONS only — a plain item per category whose client item points at a
    // representative vehicle's original sprite. Never added to a tab's displayItems, so never spawnable.
    public static final MchHolder<Item> ICON_HELI = item("tab_helicopter", Item::new, new Item.Properties());
    public static final MchHolder<Item> ICON_PLANE = item("tab_plane", Item::new, new Item.Properties());
    public static final MchHolder<Item> ICON_TANK = item("tab_tank", Item::new, new Item.Properties());
    public static final MchHolder<Item> ICON_VEHICLE = item("tab_vehicle", Item::new, new Item.Properties());

    // ---- the 112 per-vehicle spawn items, scanned from the bundled configs at init ----
    private static final Map<Category, List<MchHolder<VehicleSpawnItem>>> ITEMS_BY_CATEGORY = new EnumMap<>(Category.class);
    private static final Map<String, MchHolder<VehicleSpawnItem>> ITEM_BY_NAME = new HashMap<>();

    /** Register one spawn item per bundled/pack config, keyed by the config base-name (e.g. {@code ah-64}). Call from
     *  {@link MCHeli#onInitialize} after {@code MchContentPacks.discover()} and before the creative tabs are built. */
    public static void registerVehicles() {
        // The shared bundled+content-pack resource set, so pack config filenames mint spawn items here too.
        mcheli.agnostic.spi.ResourceSource res = mcheli.dependent.port.MchContentPacks.resources();
        // The item-id namespace is SHARED: a config base-name becomes an item id, colliding with (a) a name already
        // minted in another category, and (b) the non-vehicle items already registered (fuel/wrench/container/tab_*).
        // A content pack ships ARBITRARY third-party filenames, so BOTH — plus an illegal/uppercase name — must be
        // handled by skip+warn, never by letting Registry.register throw out of onInitialize (a hard crash).
        for (Category cat : Category.values()) {
            List<String> names = new ArrayList<>();
            for (String file : res.list(cat.dir)) {
                if (!file.endsWith(".txt")) {
                    continue;
                }
                // Lowercase to match the manager (MCH_InfoManagerBase lowercases the config name), so the item id, the
                // synced config name, and infoFor() all agree — and an uppercase pack filename becomes a legal id.
                String name = file.substring(0, file.length() - 4).toLowerCase(java.util.Locale.ROOT);
                if (Identifier.tryBuild(MCHeli.MODID, name) == null) {
                    LOG.warn("MCHeli: skipping content-pack config '{}' in {} — illegal id (only [a-z0-9/._-] allowed)",
                        file, cat.dir);
                    continue;
                }
                names.add(name);
            }
            Collections.sort(names); // Files.list order is filesystem-dependent — sort for a stable menu
            List<MchHolder<VehicleSpawnItem>> list = new ArrayList<>();
            for (String name : names) {
                if (ITEM_IDS.contains(name)) {
                    LOG.warn("MCHeli: skipping vehicle config '{}' in {} — that id is already registered (first wins)",
                        name, cat.dir);
                    continue;
                }
                MchHolder<VehicleSpawnItem> item = item(name,
                    props -> new VehicleSpawnItem(cat, name, props), new Item.Properties().stacksTo(1));
                list.add(item);
                ITEM_BY_NAME.put(name, item);
            }
            ITEMS_BY_CATEGORY.put(cat, list);
            if (list.isEmpty()) {
                LOG.error("MCHeli: registered ZERO {} spawn items — the creative tab will be empty (resource scan failed?)", cat.dir);
            } else {
                LOG.info("MCHeli: registered {} {} spawn items", list.size(), cat.dir);
            }
        }
        registerTabs();
    }

    /** The registered spawn item for a config name (for creative salvage drops), or null if none. */
    public static Item spawnItemFor(String configName) {
        MchHolder<VehicleSpawnItem> d = ITEM_BY_NAME.get(configName);
        return d != null ? d.get() : null;
    }

    public static List<MchHolder<VehicleSpawnItem>> itemsOf(Category cat) {
        return ITEMS_BY_CATEGORY.getOrDefault(cat, Collections.emptyList());
    }

    /** All registered spawn items (for the client 3D-icon registration). */
    public static List<VehicleSpawnItem> allSpawnItems() {
        List<VehicleSpawnItem> out = new ArrayList<>();
        for (MchHolder<VehicleSpawnItem> d : ITEM_BY_NAME.values()) {
            out.add(d.get());
        }
        return out;
    }

    // ---- one creative tab per category, enumerating every (non-UAV) vehicle in it ----
    public static MchHolder<CreativeModeTab> TAB_HELICOPTERS;
    public static MchHolder<CreativeModeTab> TAB_PLANES;
    public static MchHolder<CreativeModeTab> TAB_TANKS;
    public static MchHolder<CreativeModeTab> TAB_VEHICLES;

    private static void registerTabs() {
        TAB_HELICOPTERS = tab(Category.HELICOPTER, "helicopters", ICON_HELI);
        TAB_PLANES = tab(Category.PLANE, "planes", ICON_PLANE);
        TAB_TANKS = tab(Category.TANK, "tanks", ICON_TANK);
        TAB_VEHICLES = tab(Category.VEHICLE, "vehicles", ICON_VEHICLE);
    }

    private static MchHolder<CreativeModeTab> tab(Category cat, String key, Supplier<? extends Item> icon) {
        return register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli." + key))
            .icon(() -> new ItemStack(icon.get()))   // flat original tab-button icon
            .displayItems((params, output) -> {
                // Runs lazily at GUI open (after configs load) so isUAV() is known: UAV vehicles are hidden until the
                // UAV station is ported. The listed spawn items keep their 3D-model icons.
                for (MchHolder<VehicleSpawnItem> d : itemsOf(cat)) {
                    VehicleSpawnItem it = d.get();
                    if (!it.isUAV()) {
                        output.accept(it);
                    }
                }
                output.accept(new ItemStack(FUEL.get()));        // every category burns fuel, so every tab offers the can
                output.accept(new ItemStack(WRENCH.get()));      // ...and every vehicle can be mended with the wrench
                output.accept(new ItemStack(CONTAINER_ITEM.get())); // ...and the cargo box rides with any of them
                for (MchHolder<Item> ammo : AMMO) {                 // ...and is reloaded with the ammo items
                    output.accept(new ItemStack(ammo.get()));
                }
            })
            .build());
    }
}
