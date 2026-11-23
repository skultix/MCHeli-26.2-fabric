package mcheli.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCHeli with NTM (HBM Nuclear Tech) installed: the ammo press and assembly machine recipes are loaded (and the
 * crafting-table ones are not), and NTM's explosions treat tanks as
 * configured: the anti-tank mine does 50, a nuke and a big NTM blast destroy the tank, a small NTM blast and any
 * non-NTM explosion do normal damage. Skips when NTM is not in compat-mods/.
 */
public class MchNtmGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");
    private static int lane;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded("hbm")) {
            LOG.info("[GAMETEST-NTM] NTM not installed, skipped");
            return;
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runOnServer(MchNtmGameTest::recipes);

            int mine = explode(ctx, sp, "the anti-tank mine", (level, pos, tank) -> {
                BlockPos at = pos.below();
                level.setBlockAndUpdate(at, BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("hbm", "mine_he"))
                    .defaultBlockState());
                Object block = level.getBlockState(at).getBlock();
                block.getClass().getMethod("explode", Level.class, BlockPos.class, Entity.class).invoke(block, level, at, null);
            });
            check(mine == 50, "the anti-tank mine should do 50 to a tank, did " + mine);

            int nuke = explode(ctx, sp, "a nuclear blast", (level, pos, tank) ->
                Class.forName("com.hbm.explosion.ExplosionNukeGeneric").getMethod("dealDamage", Level.class,
                    double.class, double.class, double.class, double.class).invoke(null, level, pos.getX() + 3.0,
                    pos.getY() + 1.0, pos.getZ() + 0.5, 30.0));
            check(nuke == -1, "a nuke should destroy the tank, did " + nuke);

            int big = explode(ctx, sp, "a size-10 NTM blast", (level, pos, tank) -> vnt(level, pos, 10.0F));
            check(big == -1, "a big NTM explosion should destroy the tank, did " + big);

            int small = explode(ctx, sp, "a size-3 NTM blast", (level, pos, tank) -> vnt(level, pos, 3.0F));
            check(small >= 0, "a small NTM explosion should only damage the tank");

            int vanilla = explode(ctx, sp, "a size-10 vanilla explosion", (level, pos, tank) ->
                level.explode(null, pos.getX() + 3.0, pos.getY() + 1.0, pos.getZ() + 0.5, 10.0F, Level.ExplosionInteraction.NONE));
            check(vanilla >= 0, "a non-NTM explosion keeps its normal damage");
            LOG.info("[GAMETEST-NTM] tank damage: AT mine {}, nuke {}, NTM size 10 {}, NTM size 3 {}, vanilla size 10 {} "
                + "(-1 = destroyed)", mine, nuke, big, small, vanilla);
            LOG.info("[GAMETEST-NTM] PASS");
        }
    }

    private static void recipes(MinecraftServer server) {
        var rm = server.getRecipeManager();
        for (var ammo : MchRegistries.AMMO) {
            String id = "ammo_press/" + BuiltInRegistries.ITEM.getKey(ammo.get()).getPath();
            check(type(rm.byKey(key(id)).orElse(null)).equals("hbm:ammo_press"), id + " is not an ammo press recipe");
        }
        check(rm.byKey(key("ammo_fallback/ammo_7_62mm")).isEmpty(), "the crafting-table ammo recipe should be off with NTM");
        check(rm.byKey(key("m1a2")).isEmpty(), "the crafting-table M1A2 recipe should be off with NTM");
        int vehicles = 0;
        int assembly = 0;
        for (var cat : MchRegistries.Category.values()) {
            for (var holder : MchRegistries.itemsOf(cat)) {
                Identifier item = BuiltInRegistries.ITEM.getKey(holder.get());
                vehicles++;
                RecipeHolder<?> r = rm.byKey(key("assembly_machine/" + item.getPath())).orElse(null);
                if (r != null && type(r).equals("hbm:assembly_machine")) {
                    assembly++;
                } else {
                    LOG.warn("[GAMETEST-NTM] no assembly recipe for {}", item);
                }
            }
        }
        LOG.info("[GAMETEST-NTM] {} of {} vehicle spawn items have an assembly machine recipe", assembly, vehicles);
        check(assembly == vehicles && vehicles >= 100, "every vehicle needs an assembly recipe");
    }

    private interface Blast {
        void run(ServerLevel level, BlockPos pos, AbstractMchVehicle tank) throws Exception;
    }

    /** Damage the blast did to a fresh M1A2, or -1 if it destroyed it. */
    private static int explode(ClientGameTestContext ctx, TestSingleplayerContext sp, String what, Blast blast) {
        AbstractMchVehicle[] t = {null};
        sp.getServer().runOnServer(s -> t[0] = spawn(s, "m1a2"));
        ctx.waitTicks(40);
        sp.getServer().runOnServer(s -> {
            try {
                blast.run((ServerLevel) t[0].level(), t[0].blockPosition(), t[0]);
            } catch (Exception e) {
                throw new AssertionError(what + " failed: " + e, e);
            }
        });
        ctx.waitTicks(10);
        int[] dmg = {0};
        sp.getServer().runOnServer(s -> {
            dmg[0] = t[0].isDestroyed() ? -1 : t[0].getDamageTaken();
            t[0].discard();
        });
        LOG.info("[GAMETEST-NTM] {} -> {}", what, dmg[0] < 0 ? "destroyed" : dmg[0] + " damage");
        return dmg[0];
    }

    private static void vnt(ServerLevel level, BlockPos pos, float size) throws Exception {
        Class<?> c = Class.forName("com.hbm.explosion.vanillant.ExplosionVNT");
        Constructor<?> ctor = c.getConstructor(Level.class, double.class, double.class, double.class, float.class);
        Object e = ctor.newInstance(level, pos.getX() + 2.5, pos.getY() + 1.0, pos.getZ() + 0.5, size);
        c.getMethod("makeStandard").invoke(e);
        Method explode = c.getMethod("explode");
        explode.invoke(e);
    }

    private static AbstractMchVehicle spawn(MinecraftServer s, String config) {
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.level();
        // Lanes around the player, close enough to stay in loaded, ticking chunks, apart enough for a size-10 blast.
        int[][] lanes = {{24, 0}, {-24, 0}, {0, 24}, {0, -24}, {24, 24}, {-24, -24}, {24, -24}, {-24, 24}};
        int[] off = lanes[lane++ % lanes.length];
        int x = (int) Math.floor(p.getX()) + off[0];
        int z = (int) Math.floor(p.getZ()) + off[1];
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // A stone pad, so the vehicle is grounded and the blasts have something to stand on.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 6; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        EntityType<? extends AbstractMchVehicle> type = MchRegistries.TANK.get();
        AbstractMchVehicle v = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        v.setConfigName(config);
        v.setPos(x + 0.5, y, z + 0.5);
        level.addFreshEntity(v);
        return v;
    }

    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("mcheli", path));
    }

    private static String type(RecipeHolder<?> r) {
        return r == null ? "none" : String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(r.value().getType()));
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }
}
