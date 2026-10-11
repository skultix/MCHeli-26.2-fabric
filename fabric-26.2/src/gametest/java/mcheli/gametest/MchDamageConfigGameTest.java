package mcheli.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.dependent.MchDamageConfig;
import mcheli.dependent.entity.MchBullet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/mcheli-damage.json: real m230 and hydra70 rounds fired at a (very healthy) pig, under different configs
 * reloaded through {@code /mcheli damage reload}; the damage the pig takes must follow the config.
 */
public class MchDamageConfigGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");
    private static int lane;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(MchDamageConfig.FILE);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            try {
                int crater = crater(ctx, sp);
                LOG.info("[GAMETEST-DAMAGECFG] hydra70 into the ground broke {} blocks", crater);
                check(crater > 0, "a hydra70 (Explosion 3, no ExplosionBlock) should break blocks like Explosion 3");
                float base = shot(ctx, sp, file, "{}", "m230");
                float scaled = shot(ctx, sp, file,
                    "{\"vsTarget\": {\"living\": 0.5}, \"weapons\": {\"m230\": {\"power\": 4, \"explosionDamage\": 0}}}", "m230");
                float global = shot(ctx, sp, file, "{\"directDamage\": 3, \"vsTarget\": {\"living\": 0.5},"
                    + " \"weapons\": {\"m230\": {\"power\": 4, \"explosionDamage\": 0}}}", "m230");
                float blast = shot(ctx, sp, file, "{\"weapons\": {\"hydra70\": {\"directDamage\": 0}}}", "hydra70");
                float blast2 = shot(ctx, sp, file,
                    "{\"explosionDamage\": 2, \"weapons\": {\"hydra70\": {\"directDamage\": 0}}}", "hydra70");
                float noRockets = shot(ctx, sp, file,
                    "{\"weaponTypes\": {\"rocket\": {\"directDamage\": 0, \"explosionDamage\": 0}}}", "hydra70");
                // A broken file keeps the previous settings (rockets still harmless).
                float broken = shot(ctx, sp, file, "{ not json", "hydra70");
                LOG.info("[GAMETEST-DAMAGECFG] m230: default {} | power 4 x living 0.5 -> {} | x directDamage 3 -> {}; "
                        + "hydra70 blast only {} | explosionDamage 2 -> {} | Rocket type off -> {} | broken file -> {}",
                    base, scaled, global, blast, blast2, noRockets, broken);
                check(base > 16.0F, "default m230 hit should do its power 16 plus its small blast, got " + base);
                check(Math.abs(scaled - 2.0F) < 1e-3, "m230 power 4 at half damage vs living should do 2, got " + scaled);
                check(Math.abs(global - 6.0F) < 1e-3, "x3 global direct damage should do 6, got " + global);
                check(blast > 0.0F, "the hydra70 blast alone should still hurt, got " + blast);
                check(Math.abs(blast2 - 2.0F * blast) < 0.05F * blast, "explosionDamage 2 should double the blast: "
                    + blast + " -> " + blast2);
                check(noRockets == 0.0F, "the Rocket type at 0 should do nothing, got " + noRockets);
                check(broken == 0.0F, "a broken file should keep the previous settings, got " + broken);
                check(Files.exists(FabricLoader.getInstance().getConfigDir().resolve(MchDamageConfig.DEFAULTS_FILE)),
                    "the defaults reference file was not written");
            } finally {
                try {
                    Files.deleteIfExists(file);
                } catch (java.io.IOException ignored) {
                }
                sp.getServer().runCommand("mcheli damage reload");
            }
        }
    }

    /** Fires a default hydra70 straight down onto open ground; returns how many blocks around the impact it removed. */
    private static int crater(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int x = 8 * lane++;
        int[] before = {0};
        int[] y0 = {0};
        sp.getServer().runOnServer(server -> {
            var level = server.overworld();
            y0[0] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 6);
            before[0] = solid(level, x, y0[0]);
            MCH_WeaponInfo wi = MCH_WeaponInfoManager.get("hydra70");
            MchBullet.spawnWeapon(level, new Vec3(x + 0.5, y0[0] + 4.0, 6.5), new Vec3(0.0, -1.0, 0.0),
                MCH_WeaponBallistics.initialSpeed(wi.acceleration), 1.0F, 0.0F, wi.power, 200, null, "bullet", 0xFFFFFFFF, wi);
        });
        ctx.waitTicks(20);
        return sp.getServer().computeOnServer(server -> before[0] - solid(server.overworld(), x, y0[0]));
    }

    private static int solid(net.minecraft.server.level.ServerLevel level, int x, int y) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -4; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (!level.getBlockState(new net.minecraft.core.BlockPos(x + dx, y + dy, 6 + dz)).isAir()) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }

    /** Writes the config, reloads it by command, then fires one {@code weapon} round at a fresh pig; its health lost. */
    private static float shot(ClientGameTestContext ctx, TestSingleplayerContext sp, Path file, String json, String weapon) {
        try {
            Files.writeString(file, json);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        sp.getServer().runCommand("mcheli damage reload");
        int x = 8 * lane++;
        int[] id = {-1};
        sp.getServer().runOnServer(server -> {
            var level = server.overworld();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 6);
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            pig.setNoAi(true);
            pig.setNoGravity(true); // blasts crater the ground now; no falling into the hole
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
            pig.setHealth(1000.0F);
            pig.setPos(x + 0.5, y, 6.5);
            level.addFreshEntity(pig);
            id[0] = pig.getId();
            MCH_WeaponInfo wi = MCH_WeaponInfoManager.get(weapon);
            float speed = MCH_WeaponBallistics.initialSpeed(wi.acceleration);
            float factor = MCH_WeaponBallistics.accelerationFactor(wi.acceleration, MCH_WeaponBallistics.isBulletOrRocket(wi.type));
            MchBullet.spawnWeapon(level, new Vec3(x + 0.5, y + 0.45, 2.5), new Vec3(0.0, 0.0, 1.0), speed, factor,
                0.0F, wi.power, 200, null, "bullet", 0xFFFFFFFF, wi);
        });
        ctx.waitTicks(20);
        return sp.getServer().computeOnServer(server -> {
            var e = server.overworld().getEntity(id[0]);
            float lost = e instanceof Pig p ? 1000.0F - p.getHealth() : -1.0F;
            if (e != null) {
                e.discard();
            }
            return lost;
        });
    }
}
