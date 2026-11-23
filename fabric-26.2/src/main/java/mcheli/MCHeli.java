package mcheli;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.*;
import mcheli.dependent.control.MchControlNetwork;
import mcheli.dependent.port.NeoLogger;
import mcheli.dependent.port.MchContentPacks;
import mcheli.dependent.registry.MchRegistries;
import mcheli.dependent.registry.MchSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Fabric 26.2 common entrypoint — the port of the NeoForge {@code @Mod} constructor + {@code FMLCommonSetupEvent}.
 * Fabric runs {@link #onInitialize} once, with the registries still open and nothing loading in parallel, so the
 * construction-time registration and the common-setup config parse simply run in sequence here.
 */
public class MCHeli implements ModInitializer {
    public static final String MODID = "mcheli";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        // Discover content packs FIRST (under <gamedir>/mcheli/) so pack config filenames + sound basenames are known
        // before anything registers — the shared bundled+pack resource set drives everything below.
        MchContentPacks.discover();
        mcheli.dependent.registry.MchDataSerializers.init(); // custom synced-data serializers before any entity class
        // Scan the bundled+pack configs -> one spawn item per vehicle, then the creative tabs that list them. (Touching
        // MchRegistries also registers the 4 category EntityTypes + the fixed items/particles/menu/recipe serializer.)
        MchRegistries.registerVehicles();
        // Register a SoundEvent per content-pack .ogg, then the bundled sounds (bundled names win on a clash).
        MchSounds.registerPackSounds(MchContentPacks.packSoundBasenames());
        // Payload types (both directions) + the serverbound receivers. Content-pack client resources + the generated
        // recipe datapack are mounted by BuiltInPackSourceMixin (Fabric has no pack-finder event).
        MchControlNetwork.register();

        Vec3d forward = new Vec3d(0.0, 0.0, 1.0).rotateAroundY(Math.toRadians(90.0));
        LOGGER.info("MCHeli agnostic check: (0,0,1) rotated +90deg yaw -> {}", forward);

        // Load REAL vehicle definitions from the shared bundled+content-pack resource set (the agnostic DSL parser
        // through the composite ResourceSource). This is the same resource set registerVehicles() used, so the parsed
        // configs line up with the spawn items.
        mcheli.agnostic.spi.ResourceSource res = MchContentPacks.resources();
        NeoLogger log = new NeoLogger(LOGGER);
        MCH_HeliInfoManager.getInstance().load(res, log, "helicopters");
        MCP_PlaneInfoManager.getInstance().load(res, log, "planes");
        MCH_TankInfoManager.getInstance().load(res, log, "tanks");
        MCH_VehicleInfoManager.getInstance().load(res, log, "vehicles");
        MCH_WeaponInfoManager.getInstance().load(res, log, "weapons");
        mcheli.agnostic.hud.MCH_HudManager.getInstance().load(res, log);
        // Every weapon reloads from its own ammunition instead of its config's raw materials: a gun from its calibre's
        // rounds (one item per calibre in use, registered here, while the registries are still open), the rest from the
        // shared missile/rocket/bomb/torpedo items.
        @SuppressWarnings("unchecked")
        java.util.Collection<MCH_WeaponInfo> allWeapons = new java.util.ArrayList<>(
            ((java.util.Map<String, Object>) MCH_WeaponInfoManager.getInstance().getMap()).values().stream()
                .filter(MCH_WeaponInfo.class::isInstance).map(MCH_WeaponInfo.class::cast).toList());
        LOGGER.info("MCHeli: {} weapons reload from ammo items",
            mcheli.agnostic.weapon.MCH_AmmoClass.apply(allWeapons));
        MchRegistries.registerCalibers(mcheli.agnostic.weapon.MCH_AmmoClass.calibers(allWeapons));
        // Server damage tuning (config/mcheli-damage.json) on top of the weapon configs just parsed.
        mcheli.dependent.MchDamageConfig.load();
        mcheli.dependent.MchDamageConfig.registerCommand();
        mcheli.dependent.MchCrewProtection.register();
        MCH_WeaponInfo m230 = MCH_WeaponInfoManager.get("m230");
        if (m230 != null) {
            LOGGER.info("MCHeli weapon check: m230 -> type={} power={} reloadTime={}",
                m230.type, m230.power, m230.reloadTime);
        }
        MCH_TankInfo m1a2 = MCH_TankInfoManager.get("m1a2");
        if (m1a2 != null) {
            LOGGER.info("MCHeli config check: m1a2 -> speed={} gravity={} mobilityYawOnGround={} maxHp={}",
                m1a2.speed, m1a2.gravity, m1a2.mobilityYawOnGround, m1a2.maxHp);
        }

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            // Dev-only pipeline probe: resolve + parse the demo vehicles' REAL models through the agnostic parsers
            // (models/<category>/<name>.mqo|obj). Models are a client concern, so a shipped jar never parses them here.
            logModel(res, "helicopters/ah-64");
            logModel(res, "planes/a-10");
            logModel(res, "tanks/m1a2");
            registerSelfTests();
        }
    }

    /**
     * Dev-only headless self-tests — the port's acceptance tests. Off by default so an ordinary dev launch never spawns
     * probe entities; opt in with {@code -Dmcheli.selftests=<name>[,<name>...]} ({@code true} = {@code crush}, the
     * NeoForge default; {@code all} = every test, which may interfere with each other — prefer one at a time), plus
     * {@code -Dmcheli.selftest.halt=true} to stop the server once a verdict is logged. Via Gradle:
     * {@code ./gradlew :fabric-26.2:runServer -PmcheliSelfTests=heli}.
     */
    private static void registerSelfTests() {
        String prop = System.getProperty("mcheli.selftests");
        if (prop == null || prop.isBlank()) {
            return;
        }
        Map<String, Supplier<MchSelfTest>> tests = new LinkedHashMap<>();
        tests.put("crush", DemoCrushSelfTest::new);       // unridden tank tilts on a slope; a driven tank crushes planks
        tests.put("heli", DemoHeliSelfTest::new);         // collective lift: the heli climbs (vs the vehicle, which falls)
        tests.put("homing", DemoHomingSelfTest::new);     // a guided AA round curves onto an off-axis target
        tests.put("vtol", DemoVtolSelfTest::new);         // a Harrier engages VTOL and decelerates to a near-hover
        tests.put("bomb", DemoBombSelfTest::new);         // a b29-type bomb drops at hull velocity, falls + detonates
        tests.put("flare", DemoFlareSelfTest::new);       // flares null + kill a guided round homing on the aircraft
        tests.put("torpedo", DemoTorpedoSelfTest::new);   // torpedoes ramp to cruise speed underwater, level launch flat
        tests.put("plane", () -> new DemoForwardVehicleSelfTest(
            "PLANE", MchRegistries.PLANE, "a-10", 6.0, 11.0, 90.0, 5.0, 20.0)); // rider-gated thrust keeps the plane aloft
        tests.put("tank", DemoTankSelfTest::new);         // the tank drives forward without flying
        tests.put("bullet", DemoBulletSelfTest::new);     // a fired bullet hits + damages a target, then despawns
        tests.put("weapon", DemoWeaponSelfTest::new);     // the AH-64's config weapons build, switch and fire
        tests.put("particle", DemoParticleSelfTest::new); // muzzle-flash colour + cartridge model/trail come from config
        tests.put("hud", DemoHudSelfTest::new);           // the eval engine + hud/* configs + draw pipeline
        tests.put("heat", DemoHeatSelfTest::new);         // barrel heat fills, locks out at the cap, then cools
        tests.put("hp", DemoHpSelfTest::new);             // damage/armor formula + the eject-explode-fall wreck
        tests.put("crewfuel", DemoCrewFuelSelfTest::new); // multi-seat + gunner weapons + ammo/fuel economy
        tests.put("contentpack", DemoContentPackSelfTest::new); // a synthetic pack is discovered, parsed and spawned
        tests.put("cas", DemoCasSelfTest::new);           // a called-in CAS strike rains bombs onto the target
        tests.put("dispenser", DemoDispenserSelfTest::new); // a torch dispenser places torches across its radius

        String sel = prop.trim().toLowerCase(Locale.ROOT);
        if (sel.equals("true")) {
            sel = "crush";
        }
        Iterable<String> names = sel.equals("all") ? tests.keySet() : java.util.Arrays.asList(sel.split("\\s*,\\s*"));
        for (String name : names) {
            Supplier<MchSelfTest> t = tests.get(name);
            if (t == null) {
                LOGGER.error("MCHeli: unknown self-test '{}' (known: {})", name, tests.keySet());
                continue;
            }
            MchSelfTest.register(t.get());
            LOGGER.info("MCHeli: self-test '{}' registered", name);
        }
    }

    private void logModel(mcheli.agnostic.spi.ResourceSource res, String name) {
        ModelHandle h = res.loadModel(name);
        if (h instanceof MchModel m) {
            LOGGER.info("MCHeli model check: {} -> {} groups, {} faces; sizeXYZ=[{}, {}, {}], X=[{}..{}] Y=[{}..{}] Z=[{}..{}]",
                name, m.groups().size(), m.getFaceNum(), m.sizeX, m.sizeY, m.sizeZ,
                m.minX, m.maxX, m.minY, m.maxY, m.minZ, m.maxZ);
        } else {
            LOGGER.warn("MCHeli model check: {} did NOT load", name);
        }
    }
}
