package mcheli.dependent.entity;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hull-shaped hit testing for OTHER mods' projectiles. A vehicle's entity box is the small physics core (default
 * 2.0×0.7, the reference {@code setSize}) — the flight/ground simulation depends on it, so it can't grow. But every
 * config also declares part hitboxes ({@code BoundingBox = ...}) that cover the real hull, turret, wings and tail, and
 * MCHeli's own rounds already test against them ({@link AbstractMchVehicle#clipParts}). Vanilla projectiles
 * ({@code ProjectileUtilMixin}) and TaCZ bullets ({@code TaczEntityUtilMixin}) are routed through the same test so a
 * shot that visibly hits the hull hits the vehicle.
 */
public final class VehicleHitboxes {
    private VehicleHitboxes() {}

    /** How far beyond a path segment to look for vehicle cores: the largest aircraft's part boxes reach ~20 blocks
     *  from the core, so a wingtip hit must still find the vehicle. */
    public static final double SEARCH_REACH = 24.0;

    /** The vehicle part box a segment strikes first, and where. */
    public record Hit(AbstractMchVehicle vehicle, Vec3 pos) {
        public double distanceSqr(Vec3 from) {
            return from.distanceToSqr(this.pos);
        }
    }

    /**
     * Nearest vehicle whose core or part boxes the segment {@code [start, end]} enters, or null. A vehicle the shooter
     * (or the projectile's owner) is riding is never hit — a gunner's own rounds leave their aircraft — and
     * {@code filter} (the caller's own "can hit this entity" rule) must accept it.
     */
    public static Hit nearest(Level level, Entity projectile, Vec3 start, Vec3 end, Predicate<Entity> filter) {
        Entity owner = projectile instanceof net.minecraft.world.entity.projectile.Projectile p ? p.getOwner() : null;
        AABB search = new AABB(start, end).inflate(SEARCH_REACH);
        Hit best = null;
        for (AbstractMchVehicle v : level.getEntitiesOfClass(AbstractMchVehicle.class, search,
                v -> v != projectile && v.isPickable() && !ridesIn(projectile, v) && !ridesIn(owner, v))) {
            if (filter != null && !filter.test(v)) {
                continue;
            }
            Vec3 h = v.clipParts(start, end);
            if (h != null && (best == null || start.distanceToSqr(h) < best.distanceSqr(start))) {
                best = new Hit(v, h);
            }
        }
        return best;
    }

    private static boolean ridesIn(Entity e, AbstractMchVehicle v) {
        return e != null && (e == v || e.isPassengerOfSameVehicle(v));
    }
}
