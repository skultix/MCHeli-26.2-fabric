package mcheli.fabric;

import mcheli.agnostic.value.Vec3d;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 26.2 common entrypoint. SKELETON ONLY: it proves the module wiring (agnostic layer on the runtime
 * classpath, shared mcheli-assets bundled) before the platform layer is ported from neoforge-1.21.1.
 */
public class MCHeliFabric implements ModInitializer {
    public static final String MODID = "mcheli";
    private static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        Vec3d forward = new Vec3d(0.0, 0.0, 1.0).rotateAroundY(Math.toRadians(90.0));
        LOGGER.info("MCHeli (Fabric 26.2) agnostic check: (0,0,1) rotated +90deg yaw -> {}", forward);
        boolean assets = MCHeliFabric.class.getResource("/assets/mcheli/helicopters/ah-64.txt") != null;
        LOGGER.info("MCHeli (Fabric 26.2) shared assets bundled: {}", assets);
    }
}
