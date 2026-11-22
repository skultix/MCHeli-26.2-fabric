package mcheli.dependent;

import java.util.ArrayList;
import java.util.List;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The crew rules from {@code crew} in {@code config/mcheli-damage.json}: a player riding an intact vehicle takes no
 * damage (the vehicle's hit points are theirs), and dies when the vehicle is destroyed. Damage that bypasses
 * invulnerability ({@code /kill}, the void) still applies.
 */
public final class MchCrewProtection {
    private MchCrewProtection() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof Player) || !MchDamageConfig.settings().crew.protectedWhileMounted
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true;
            }
            return !(entity.getVehicle() instanceof AbstractMchVehicle v) || v.isDestroyed();
        });
    }

    /** The players aboard, captured before the destroyed vehicle ejects them. */
    public static List<Player> crewOf(AbstractMchVehicle vehicle) {
        List<Player> crew = new ArrayList<>();
        if (MchDamageConfig.settings().crew.diesWithVehicle) {
            for (Entity p : vehicle.getPassengers()) {
                if (p instanceof Player pl && !pl.isCreative() && !pl.isSpectator()) {
                    crew.add(pl);
                }
            }
        }
        return crew;
    }

    /** Kills the (already ejected) crew of a destroyed vehicle, credited to whoever destroyed it. */
    public static void killCrew(ServerLevel level, AbstractMchVehicle vehicle, List<Player> crew, Entity killer) {
        for (Player p : crew) {
            if (p.isAlive()) {
                p.hurtServer(level, level.damageSources().explosion(vehicle, killer), 1.0E6F);
            }
        }
    }
}
