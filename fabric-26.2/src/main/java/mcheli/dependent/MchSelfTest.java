package mcheli.dependent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * A dev-only headless self-test (the {@code Demo*SelfTest} classes). NeoForge subscribed these to the game event bus;
 * on Fabric {@link #register} wires the two hooks they use to the Fabric lifecycle/tick events.
 *
 * <p>26.x has no always-loaded spawn chunks (removed in 1.21.9), so on a playerless server nothing near spawn ticks —
 * the 1.21.1 tests silently relied on that region. Before the first test starts, the harness holds a non-persistent
 * loading + simulation ticket over the spawn area so the probes' entities actually tick, as they did on 1.21.1.
 */
public interface MchSelfTest {
    /** Server finished starting (NeoForge {@code ServerStartedEvent}). */
    default void onServerStarted(MinecraftServer server) {}

    /** End of every server tick (NeoForge {@code ServerTickEvent.Post}). */
    default void onServerTick(MinecraftServer server) {}

    /** Chunk radius of the simulated area kept around world spawn while self-tests run. */
    int SPAWN_TICKET_RADIUS = 8;

    static void register(MchSelfTest test) {
        Harness.init();
        ServerLifecycleEvents.SERVER_STARTED.register(test::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(test::onServerTick);
    }

    /** One-time harness setup (ticket type + the spawn-area ticket hook, registered before any test's own hook). */
    final class Harness {
        private Harness() {}
        private static TicketType ticket;

        static void init() {
            if (ticket != null) {
                return;
            }
            ticket = Registry.register(BuiltInRegistries.TICKET_TYPE,
                mcheli.dependent.registry.MchRegistries.id("selftest"),
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                ServerLevel level = server.overworld();
                ChunkPos center = ChunkPos.containing(level.getRespawnData().pos());
                // Load the area BEFORE the tests' own SERVER_STARTED hooks spawn into it (1.21.1 had spawn chunks loaded by
                // then); managedBlock keeps running server tasks while waiting, so chunk loading can complete.
                java.util.concurrent.CompletableFuture<?> loaded =
                    level.getChunkSource().addTicketAndLoadWithRadius(ticket, center, SPAWN_TICKET_RADIUS);
                server.managedBlock(loaded::isDone);
            });
        }
    }
}
