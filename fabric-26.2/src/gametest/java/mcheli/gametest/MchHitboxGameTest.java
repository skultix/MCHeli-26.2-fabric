package mcheli.gametest;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hull hitboxes for vanilla projectiles (ProjectileUtilMixin): an arrow aimed at an AH-6's cabin — inside its config
 * part box but ABOVE the 0.7-tall physics core — must hit, while a control arrow over the top must miss.
 */
public class MchHitboxGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode creative @a");
            int hullHit = arrowAt(ctx, sp, 0, 2.3);
            int overTop = arrowAt(ctx, sp, 40, 7.0);
            LOG.info("[GAMETEST-HITBOX] arrow into ah-6 cabin (above the core) -> {} damage; arrow over the top -> {}",
                hullHit, overTop);
            if (hullHit <= 0) {
                throw new AssertionError("an arrow into the AH-6 cabin part box did no damage");
            }
            if (overTop != 0) {
                throw new AssertionError("an arrow over the AH-6 still hit it");
            }
            sp.getServer().runOnServer(MchHitboxGameTest::everyVehicleHull);
        }
    }

    /**
     * Every vehicle, tilted (yaw 37, pitch 12, roll 15): rays fired from random directions at points just inside its drawn
     * {@code $body} mesh surface — placed in the world with the RENDERER's transform, computed independently here — must hit
     * the vehicle no later than the target point; rays passing above the model must miss. Validates the hull build and
     * the local-frame transform for the whole fleet.
     */
    private static void everyVehicleHull(net.minecraft.server.MinecraftServer server) {
        var level = server.overworld();
        java.util.Random rnd = new java.util.Random(42);
        java.util.List<String> bad = new java.util.ArrayList<>();
        int vehicles = 0;
        long hits = 0;
        long rays = 0;
        for (mcheli.dependent.item.VehicleSpawnItem item : MchRegistries.allSpawnItems()) {
            AbstractMchVehicle v = MchRegistries.entityTypeFor(item.category).create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            v.setConfigName(item.configName);
            v.setPos(0.5, 200.0, 0.5);
            v.setYRot(37.0F);
            v.setXRot(12.0F);
            v.setRollAngle(15.0F);
            var h = mcheli.dependent.port.MchContentPacks.resources().loadModel(item.category.dir + "/" + item.configName);
            if (!(h instanceof mcheli.agnostic.model.MchModel m)) {
                continue;
            }
            java.util.List<mcheli.agnostic.model.ModelVertex> body = new java.util.ArrayList<>();
            boolean inBody = false;
            for (var g : m.groups()) {
                if (g == null) {
                    continue;
                }
                if (g.name.startsWith("$") || !(m instanceof mcheli.agnostic.model.MqoModel)) {
                    inBody = g.name.equalsIgnoreCase("$body");
                }
                if (inBody) {
                    for (var f : g.faces) {
                        java.util.Collections.addAll(body, f.vertices);
                    }
                }
            }
            if (body.isEmpty()) {
                continue; // no $body (drawn whole): covered by the build audit, not sampled here
            }
            vehicles++;
            // Aim a quarter block INSIDE the surface (toward the body centroid): an exact convex corner vertex is only grazed.
            double cx = 0, cy = 0, cz = 0;
            for (var bv : body) {
                cx += bv.x;
                cy += bv.y;
                cz += bv.z;
            }
            cx /= body.size();
            cy /= body.size();
            cz /= body.size();
            org.joml.Quaternionf rot = new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(-37.0)).rotateX((float) Math.toRadians(12.0)).rotateZ((float) Math.toRadians(15.0));
            int vh = 0;
            int vr = 0;
            for (int i = 0; i < 40; i++) {
                var mv = body.get(rnd.nextInt(body.size()));
                org.joml.Vector3f in = new org.joml.Vector3f((float) (cx - mv.x), (float) (cy - mv.y), (float) (cz - mv.z));
                if (in.length() > 1.0e-4F) {
                    in.normalize(0.25F);
                }
                org.joml.Vector3f p = new org.joml.Vector3f(mv.x + in.x, mv.y + in.y, mv.z + in.z).rotate(rot);
                net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(
                    v.getX() + p.x, v.getY() + p.y - m.minY, v.getZ() + p.z);
                net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(
                    rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
                net.minecraft.world.phys.Vec3 start = target.add(dir.scale(60.0));
                net.minecraft.world.phys.Vec3 hit = v.clipParts(start, target.subtract(dir.scale(0.75)));
                vr++;
                if (hit != null && start.distanceTo(hit) <= 60.0 + 0.75) {
                    vh++;
                }
            }
            // Over the top: horizontal rays 3 blocks above the highest point the model can reach in any orientation.
            double top = v.getY() + Math.sqrt(m.maxX * m.maxX + (m.maxY - m.minY) * (m.maxY - m.minY) + m.maxZ * m.maxZ)
                + Math.max(Math.abs(m.minX), Math.abs(m.minZ)) + 3.0;
            boolean overHit = v.clipParts(new net.minecraft.world.phys.Vec3(-80, top, 0.5),
                new net.minecraft.world.phys.Vec3(80, top, 0.5)) != null;
            hits += vh;
            rays += vr;
            if (vh < vr * 0.95 || overHit) {
                bad.add(item.configName + "(" + vh + "/" + vr + (overHit ? ", hit above" : "") + ")");
            }
        }
        LOG.info("[GAMETEST-HITBOX] fleet hull check: {} vehicles, {}/{} rays at points just inside the body surface hit; failing: {}",
            vehicles, hits, rays, bad);
        if (!bad.isEmpty()) {
            throw new AssertionError("hull check failed for " + bad);
        }
    }

    /** Fresh ah-6 8 blocks ahead of a player at (x, surface, 0); fire an arrow at {@code height} above its feet. */
    private static int arrowAt(ClientGameTestContext ctx, TestSingleplayerContext sp, int x, double height) {
        int[] id = {-1};
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var level = p.level();
            int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
            p.teleportTo(x + 0.5, y0, 0.5);
            double vz = 8.5;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, (int) vz);
            AbstractMchVehicle heli = MchRegistries.HELI.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            heli.setConfigName("ah-6");
            heli.setPos(x + 0.5, y, vz);
            level.addFreshEntity(heli);
            id[0] = heli.getId();
        });
        ctx.waitTicks(10); // let the server pose the part boxes
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var heli = p.level().getEntity(id[0]);
            Arrow arrow = new Arrow(p.level(), p, new ItemStack(Items.ARROW), null);
            arrow.setPos(p.getX(), heli.getY() + height, p.getZ());
            arrow.setNoGravity(true);
            arrow.shoot(0.0, 0.0, 1.0, 3.0F, 0.0F);
            p.level().addFreshEntity(arrow);
        });
        ctx.waitTicks(30);
        return sp.getServer().computeOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(id[0]);
            int dmg = e instanceof AbstractMchVehicle v ? v.getDamageTaken() : -1;
            if (e != null) {
                e.discard();
            }
            return dmg;
        });
    }
}
