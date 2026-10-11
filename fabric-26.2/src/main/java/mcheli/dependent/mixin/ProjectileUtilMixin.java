package mcheli.dependent.mixin;

import java.util.function.Predicate;
import mcheli.dependent.entity.VehicleHitboxes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla (and most modded) projectiles — arrows, tridents, snowballs, fireballs — find their target through this
 * {@code ProjectileUtil.getEntityHitResult} overload, which only clips entity boxes. A vehicle's entity box is its small
 * physics core, so also test the vehicles' hull part boxes and keep whichever hit is nearer the projectile's start.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
        + "Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At("RETURN"), cancellable = true)
    private static void mcheli$vehicleHull(Level level, Entity projectile, Vec3 start, Vec3 end, AABB box,
                                          Predicate<Entity> filter, float margin,
                                          CallbackInfoReturnable<EntityHitResult> cir) {
        VehicleHitboxes.Hit hit = VehicleHitboxes.nearest(level, projectile, start, end, filter);
        if (hit == null) {
            return;
        }
        EntityHitResult current = cir.getReturnValue();
        if (current == null || hit.distanceSqr(start) < start.distanceToSqr(current.getLocation())) {
            cir.setReturnValue(new EntityHitResult(hit.vehicle(), hit.pos()));
        }
    }

    /** The multi-hit search 26.x arrows use (piercing or not): swap each vehicle's core-box result for its hull hit and
     *  add vehicles struck only by the hull, keeping the list nearest-first. */
    @Inject(method = "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
        + "Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;Z)Ljava/util/Collection;",
        at = @At("RETURN"), cancellable = true)
    private static void mcheli$vehicleHulls(Level level, Entity projectile, Vec3 start, Vec3 end, AABB box,
                                           Predicate<Entity> filter, float margin,
                                           net.minecraft.world.level.ClipContext.Block block, boolean includeInside,
                                           CallbackInfoReturnable<java.util.Collection<EntityHitResult>> cir) {
        java.util.List<mcheli.dependent.entity.AbstractMchVehicle> seen = new java.util.ArrayList<>();
        java.util.List<EntityHitResult> hulls = new java.util.ArrayList<>();
        for (int guard = 0; guard < 8; guard++) {
            VehicleHitboxes.Hit hit = VehicleHitboxes.nearest(level, projectile, start, end,
                e -> (filter == null || filter.test(e)) && !seen.contains(e));
            if (hit == null) {
                break;
            }
            seen.add(hit.vehicle());
            hulls.add(new EntityHitResult(hit.vehicle(), hit.pos()));
        }
        if (hulls.isEmpty()) {
            return;
        }
        java.util.List<EntityHitResult> out = new java.util.ArrayList<>();
        for (EntityHitResult r : cir.getReturnValue()) {
            if (!(r.getEntity() instanceof mcheli.dependent.entity.AbstractMchVehicle)) {
                out.add(r);
            }
        }
        out.addAll(hulls);
        out.sort(java.util.Comparator.comparingDouble(r -> start.distanceToSqr(r.getLocation())));
        cir.setReturnValue(out);
    }
}
