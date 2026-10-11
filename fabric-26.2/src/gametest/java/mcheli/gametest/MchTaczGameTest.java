package mcheli.gametest;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaCZ interop: fires a real TaCZ RPG-7 (through TaCZ's own gun API) at an M1A2 — a direct hit, then a near miss — and
 * checks the tank takes MCHeli-scale damage (TaczCompat). Skipped unless TaCZ is present (dev: fabric-26.2/compat-mods).
 * TaCZ is driven by reflection so this source set compiles without it.
 */
public class MchTaczGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            LOG.info("[GAMETEST-TACZ] TaCZ not present; skipped");
            return;
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("tp @a ~ ~ ~ 0 0");
            ctx.waitTicks(20);

            // Each scenario on untouched ground 40 blocks apart: the rocket and TNT blasts crater the terrain, which would
            // change the next scenario's impact point and the blast exposure (getSeenPercent).
            moveTo(ctx, sp, 0);
            int direct = fireAt(ctx, sp, 0.35, "direct");
            moveTo(ctx, sp, 40);
            int tnt = tntBeside(ctx, sp, 2.0);
            moveTo(ctx, sp, 80);
            // The turret sits well above the 0.7-tall physics core: only the hull part boxes (TaczEntityUtilMixin) catch it.
            int turret = fireAt(ctx, sp, 2.4, "turret");
            LOG.info("[GAMETEST-TACZ] m1a2 (maxHp 330): RPG-7 direct hit -> {}, RPG-7 into the turret -> {}, "
                + "vanilla TNT 2 blocks away -> {} damage", direct, turret, tnt);
            if (turret < 60) {
                throw new AssertionError("RPG-7 into the m1a2 turret did only " + turret + " damage");
            }
            if (direct < 60) {
                throw new AssertionError("RPG-7 direct hit did only " + direct + " damage to an m1a2");
            }
        }
    }

    /** Teleport the player to (x, surface, 0) facing +Z, on fresh ground. */
    private static void moveTo(ClientGameTestContext ctx, TestSingleplayerContext sp, int x) {
        int y = sp.getServer().computeOnServer(server -> server.overworld()
            .getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0));
        sp.getServer().runCommand("tp @a " + x + " " + y + " 0 0 0");
        ctx.waitTicks(20);
        sp.getConnection().waitForChunksRender();
    }

    /** Spawn a fresh m1a2 8 blocks ahead (inside the RPG-7 rocket's flat flight), fire one RPG-7 at its hull and return
     *  the tank's accumulated damage. (TaCZ detonates a rocket where it was at the START of the tick it hit something,
     *  so near-miss geometry isn't controllable with this fast round — only the direct hit is asserted.) */
    private static int fireAt(ClientGameTestContext ctx, TestSingleplayerContext sp, double aimHeight, String label) {
        int[] tankId = {-1};
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var level = p.level();
            double x = p.getX();
            double z = p.getZ() + 8.0; // within the RPG-7 rocket's flat flight (it drops ~8 blocks out)
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            AbstractMchVehicle tank = MchRegistries.TANK.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            tank.setConfigName("m1a2");
            tank.setPos(x, y, z);
            level.addFreshEntity(tank);
            tankId[0] = tank.getId();

            ItemStack rpg = buildGun(server, "tacz:rpg7");
            p.setItemInHand(InteractionHand.MAIN_HAND, rpg);
            Object op = operator(p);
            invoke(op, "draw", new Class<?>[] {Supplier.class}, (Supplier<ItemStack>) () -> p.getMainHandItem());
        });
        ctx.waitTicks(40); // draw time
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var tank = p.level().getEntity(tankId[0]);
            double dx = tank.getX() - p.getX();
            double dz = tank.getZ() - p.getZ();
            double dy = tank.getY() + aimHeight - p.getEyeY();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            p.setYRot(yaw);
            p.setXRot(pitch);
            float fYaw = yaw;
            float fPitch = pitch;
            Object result = invoke(operator(p), "shoot", new Class<?>[] {Supplier.class, Supplier.class},
                (Supplier<Float>) () -> fPitch, (Supplier<Float>) () -> fYaw);
            LOG.info("[GAMETEST-TACZ] {} shot: {}", label, result);
        });
        ctx.waitTicks(60);
        int damage = sp.getServer().computeOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(tankId[0]);
            return e instanceof AbstractMchVehicle v ? v.getDamageTaken() : -1;
        });
        sp.getServer().runOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(tankId[0]);
            if (e != null) {
                e.discard();
            }
        });
        return damage;
    }

    /** Spawn a fresh m1a2 14 blocks ahead and detonate a TNT {@code dist} blocks beside it; return its damage. */
    private static int tntBeside(ClientGameTestContext ctx, TestSingleplayerContext sp, double dist) {
        int[] tankId = {-1};
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var level = p.level();
            double x = p.getX();
            double z = p.getZ() + 14.0;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            AbstractMchVehicle tank = MchRegistries.TANK.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            tank.setConfigName("m1a2");
            tank.setPos(x, y, z);
            level.addFreshEntity(tank);
            tankId[0] = tank.getId();
            var tnt = new net.minecraft.world.entity.item.PrimedTnt(level, x + dist, y, z, null);
            tnt.setFuse(1);
            level.addFreshEntity(tnt);
        });
        ctx.waitTicks(20);
        int damage = sp.getServer().computeOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(tankId[0]);
            return e instanceof AbstractMchVehicle v ? v.getDamageTaken() : -1;
        });
        sp.getServer().runOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(tankId[0]);
            if (e != null) {
                e.discard();
            }
        });
        return damage;
    }

    private static ItemStack buildGun(net.minecraft.server.MinecraftServer server, String id) {
        try {
            Class<?> builder = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            Object b = builder.getMethod("create").invoke(null);
            b = builder.getMethod("setId", Identifier.class).invoke(b, Identifier.parse(id));
            b = builder.getMethod("setAmmoCount", int.class).invoke(b, 1);
            b = builder.getMethod("setAmmoInBarrel", boolean.class).invoke(b, true);
            return (ItemStack) builder.getMethod("build", net.minecraft.core.HolderLookup.Provider.class)
                .invoke(b, server.registryAccess());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not build TaCZ gun " + id, e);
        }
    }

    private static Object operator(ServerPlayer p) {
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            return op.getMethod("fromLivingEntity", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no TaCZ gun operator", e);
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = Class.forName("com.tacz.guns.api.entity.IGunOperator").getMethod(name, types);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("TaCZ call " + name + " failed", e);
        }
    }
}
