package mcheli.dependent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * A dev-only headless self-test (the {@code Demo*SelfTest} classes). NeoForge subscribed these to the game event bus;
 * on Fabric {@link #register} wires the two hooks they use to the Fabric lifecycle/tick events.
 */
public interface MchSelfTest {
    /** Server finished starting (NeoForge {@code ServerStartedEvent}). */
    default void onServerStarted(MinecraftServer server) {}

    /** End of every server tick (NeoForge {@code ServerTickEvent.Post}). */
    default void onServerTick(MinecraftServer server) {}

    static void register(MchSelfTest test) {
        ServerLifecycleEvents.SERVER_STARTED.register(test::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(test::onServerTick);
    }
}
