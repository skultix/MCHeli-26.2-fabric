package mcheli.gametest;

import java.lang.reflect.Field;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every weapon reloads only from its own ammo item, paid per round (rockets, missiles, bombs, shells) or per belt
 * (Vehicle Bullets 25, Autocannon Rounds 20). A reload fills the weapon to its maximum, or as far as the player's ammo goes.
 */
public class MchReloadGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            AbstractMchVehicle[] v = {null, null};
            sp.getServer().runOnServer(s -> {
                v[0] = spawn(s, MchRegistries.TANK.get(), "t-90", 12);
                v[1] = spawn(s, MchRegistries.HELI.get(), "ah-64", -12);
            });
            ctx.waitTicks(40);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                // 8 Hydra rockets cost 8 Rockets (one per round); 3 Rockets reload 3 (partial).
                reload(p, v[1], "hydra70", MchRegistries.AMMO_ROCKET.get(), 3, 1);
                reload(p, v[1], "hydra70", MchRegistries.AMMO_ROCKET.get(), 8, 1);
                // The wrong ammo is refused: Bombs do not load rockets.
                wrong(p, v[1], "hydra70", MchRegistries.AMMO_BOMB.get());
                wrong(p, v[1], "m230", MchRegistries.AMMO_TANK_SHELL.get());
                // A big shell is one round; with plenty, it fills to the maximum and uses only what it needs.
                reload(p, v[0], "2a46m-1_he", MchRegistries.AMMO_TANK_SHELL.get(), 3, 1);
                reload(p, v[0], "2a46m-1_he", MchRegistries.AMMO_TANK_SHELL.get(), 64, 1);
                // The 30 mm M230 takes Autocannon Rounds (belts of 20), the coaxial MG Vehicle Bullets (belts of 25).
                reload(p, v[1], "m230", MchRegistries.AMMO_AUTOCANNON.get(), 2, 20);
                reload(p, v[0], "mg_coaxial", MchRegistries.AMMO_BULLET.get(), 2, 25);
                // Nothing in hand: refused.
                p.getInventory().clearContent();
                WeaponSlot rockets = slot(v[1], "hydra70");
                check(rockets.totalAmmo() >= rockets.maxAmmo() || !v[1].supplyAmmoFromPlayer(p, index(v[1], "hydra70")),
                    "a reload with no ammo must be refused");
            });
            LOG.info("[GAMETEST-RELOAD] PASS");
        }
    }

    /** Give {@code held} of {@code ammo}, reload, and check the items used and rounds gained match the per-item rate. */
    private static void reload(ServerPlayer p, AbstractMchVehicle v, String weapon, Item ammo, int held, int perItem) {
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(ammo, held));
        WeaponSlot w = slot(v, weapon);
        int before = w.totalAmmo();
        int missing = w.maxAmmo() - before;
        boolean ok = v.supplyAmmoFromPlayer(p, index(v, weapon));
        int used = held - p.getInventory().getItem(0).getCount();
        int gained = w.totalAmmo() - before;
        int expectUsed = Math.min(held, (missing + perItem - 1) / perItem);
        int expectGained = Math.min(missing, expectUsed * perItem);
        LOG.info("[GAMETEST-RELOAD] {} {}: {}/{} rounds, holding {} -> used {}, gained {} (now {}/{})", v.configName(),
            weapon, before, w.maxAmmo(), held, used, gained, w.totalAmmo(), w.maxAmmo());
        check(ok == (missing > 0), weapon + ": reload granted " + ok + " with " + missing + " missing");
        check(used == expectUsed && gained == expectGained, weapon + ": used " + used + " (expected " + expectUsed
            + "), gained " + gained + " (expected " + expectGained + ")");
    }

    /** Holding only the wrong ammo, the reload is refused and nothing is taken. */
    private static void wrong(ServerPlayer p, AbstractMchVehicle v, String weapon, Item ammo) {
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(ammo, 16));
        WeaponSlot w = slot(v, weapon);
        int before = w.totalAmmo();
        boolean ok = v.supplyAmmoFromPlayer(p, index(v, weapon));
        check(!ok && w.totalAmmo() == before && p.getInventory().getItem(0).getCount() == 16,
            weapon + " must not reload from " + ammo);
    }

    private static int index(AbstractMchVehicle v, String weapon) {
        VehicleWeapons ws = weapons(v);
        for (int i = 0; i < ws.size(); i++) {
            if (ws.get(i).weaponName.equalsIgnoreCase(weapon)) {
                return i;
            }
        }
        throw new AssertionError(v.configName() + " has no " + weapon);
    }

    private static WeaponSlot slot(AbstractMchVehicle v, String weapon) {
        return weapons(v).get(index(v, weapon));
    }

    private static VehicleWeapons weapons(AbstractMchVehicle v) {
        try {
            Field f = AbstractMchVehicle.class.getDeclaredField("weapons");
            f.setAccessible(true);
            return (VehicleWeapons) f.get(v);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static AbstractMchVehicle spawn(MinecraftServer s, EntityType<? extends AbstractMchVehicle> type, String config, int dx) {
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.level();
        int x = (int) Math.floor(p.getX()) + dx;
        int z = (int) Math.floor(p.getZ());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        for (int ax = -5; ax <= 5; ax++) {
            for (int az = -5; az <= 5; az++) {
                level.setBlockAndUpdate(new BlockPos(x + ax, y - 1, z + az), Blocks.STONE.defaultBlockState());
            }
        }
        AbstractMchVehicle v = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        v.setConfigName(config);
        v.setPos(x + 0.5, y, z + 0.5);
        level.addFreshEntity(v);
        return v;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }
}
