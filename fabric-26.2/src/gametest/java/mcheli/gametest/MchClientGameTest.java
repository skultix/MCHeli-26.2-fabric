package mcheli.gametest;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Scripted client smoke test of the port's client side: a real singleplayer world with one vehicle per category, the
 * pilot seat (cockpit camera + HUD), third person, and the 3D spawn-item icons in the hotbar. Each step is captured as a
 * screenshot (build/run/clientGameTest/screenshots) for visual inspection; any client exception fails the run.
 */
public class MchClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");

            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.level();
                double px = p.getX();
                double pz = p.getZ();
                spawn(level, MchRegistries.HELI.get(), "ah-64", px, pz + 16);
                spawn(level, MchRegistries.PLANE.get(), "a-10", px - 16, pz + 26);
                spawn(level, MchRegistries.TANK.get(), "m1a2", px + 12, pz + 18);
                spawn(level, MchRegistries.VEHICLE.get(), "mk15", px + 4, pz + 8);
                // Spawn items in the hotbar: their icons are the 3D vehicle models (SpecialModelRenderer).
                String[] icons = {"ah-64", "a-10", "m1a2", "ch47", "kv-2"};
                for (int i = 0; i < icons.length; i++) {
                    var item = MchRegistries.spawnItemFor(icons[i]);
                    if (item != null) {
                        p.getInventory().setItem(i, new ItemStack(item));
                    }
                }
                p.getInventory().setItem(6, new ItemStack(MchRegistries.FUEL.get()));
                p.getInventory().setItem(7, new ItemStack(MchRegistries.WRENCH.get()));
            });
            sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
            ctx.waitTicks(60);
            sp.getConnection().waitForChunksRender();
            ctx.takeScreenshot("mcheli-01-vehicles");

            // Board the AH-64: pilot seat -> hull-locked cockpit camera (CameraMixin) + the config-driven HUD.
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                AbstractMchVehicle heli = p.level().getEntitiesOfClass(AbstractMchVehicle.class,
                    p.getBoundingBox().inflate(40), v -> "ah-64".equals(v.configName())).stream().findFirst().orElse(null);
                if (heli != null) {
                    p.startRiding(heli, true, true);
                }
            });
            ctx.waitTicks(40);
            ctx.takeScreenshot("mcheli-02-cockpit");

            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(20);
            ctx.takeScreenshot("mcheli-03-third-person");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).stopRiding());
            ctx.waitTicks(10);
        }
    }

    private static void spawn(ServerLevel level, EntityType<? extends AbstractMchVehicle> type, String config,
                              double x, double z) {
        AbstractMchVehicle v = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (v == null) {
            throw new AssertionError("could not create " + config);
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        v.setConfigName(config);
        v.setPos(x, y, z);
        v.setYRot(180.0F); // nose toward the player
        level.addFreshEntity(v);
    }
}
