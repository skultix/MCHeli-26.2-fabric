package mcheli.gametest;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A player inside a vehicle: protected from damage while the vehicle is intact, killed when it is destroyed, and able
 * to die in the seat (/kill) and respawn without a client crash (the reported "passengers for unknown entity" crash,
 * seen with PlayerAnimationLib installed). Runs on a dedicated server, where every packet is really encoded, and in
 * singleplayer.
 */
public class MchRiderGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer();
             TestDedicatedServerConnection connection = server.connect()) {
            connection.waitForChunksRender();
            run(ctx, server, "dedicated");
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            run(ctx, sp.getServer(), "singleplayer");
        }
    }

    private static void run(ClientGameTestContext ctx, TestServerContext server, String label) {
        server.runCommand("gamemode survival @a");
        server.runCommand("gamerule immediate_respawn false");

        // 1. Killed in the seat (/kill bypasses the rider protection), then respawn.
        mount(ctx, server, "m1a2", 6);
        server.runCommand("kill @a");
        ctx.waitTicks(30);
        ctx.takeScreenshot("mcheli-rider-" + label + "-01-dead-in-seat");
        respawn(ctx);
        ctx.takeScreenshot("mcheli-rider-" + label + "-02-respawned");

        // 2. Protected while the vehicle is intact: generic damage, fall damage, an explosion next to the hull.
        mount(ctx, server, "ah-64", 14);
        float before = health(server);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = p.level();
            p.hurtServer(level, level.damageSources().generic(), 10.0F);
            p.hurtServer(level, level.damageSources().fall(), 10.0F);
            level.explode(null, p.getX() + 1.5, p.getY(), p.getZ(), 3.0F,
                net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
        ctx.waitTicks(5);
        float after = health(server);
        LOG.info("[GAMETEST-RIDER] {}: rider health in an intact vehicle: {} -> {}", label, before, after);
        check(after >= before, "a rider in an intact vehicle took damage: " + before + " -> " + after);

        // 3. The vehicle is destroyed: the crew dies with it, and can respawn.
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            if (p.getVehicle() instanceof AbstractMchVehicle v) {
                v.hurtServer(p.level(), p.level().damageSources().explosion(null, null), 100000.0F);
            }
        });
        ctx.waitTicks(30);
        boolean dead = ctx.computeOnClient(mc -> mc.player != null && mc.player.isDeadOrDying());
        LOG.info("[GAMETEST-RIDER] {}: crew dead after the vehicle was destroyed: {}", label, dead);
        check(dead, "the crew should die when their vehicle is destroyed");
        respawn(ctx);
        LOG.info("[GAMETEST-RIDER] {} PASS", label);
    }

    private static void mount(ClientGameTestContext ctx, TestServerContext server, String config, int dz) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = p.level();
            EntityType<? extends AbstractMchVehicle> type = config.equals("m1a2") ? MchRegistries.TANK.get()
                                                                                  : MchRegistries.HELI.get();
            AbstractMchVehicle v = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            int x = (int) Math.floor(p.getX());
            int z = (int) Math.floor(p.getZ()) + dz;
            v.setConfigName(config);
            v.setPos(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
            level.addFreshEntity(v);
            p.startRiding(v, true, true);
        });
        ctx.waitTicks(20);
        boolean riding = ctx.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof AbstractMchVehicle);
        check(riding, "the client player is not riding the " + config);
    }

    private static void respawn(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.respawn();
            }
        });
        ctx.waitTicks(40);
        boolean alive = ctx.computeOnClient(mc -> mc.player != null && mc.player.isAlive() && mc.player.getVehicle() == null);
        check(alive, "the player did not respawn cleanly");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static float health(TestServerContext server) {
        float[] h = {0};
        server.runOnServer(s -> h[0] = player(s).getHealth());
        return h[0];
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }
}
