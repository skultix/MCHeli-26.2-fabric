package mcheli.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric 26.2 client entrypoint. SKELETON ONLY — client-side registration lands here during the port. */
public class MCHeliFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcheli");

    @Override
    public void onInitializeClient() {
        LOGGER.info("MCHeli (Fabric 26.2) client initialized");
    }
}
