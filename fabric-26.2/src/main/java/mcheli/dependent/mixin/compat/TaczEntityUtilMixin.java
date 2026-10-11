package mcheli.dependent.mixin.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.VehicleHitboxes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ bullets find their target with their own {@code EntityUtil} (entity boxes only), so a vehicle — whose entity box
 * is the small physics core — was only hittable dead centre. Add the vehicles' hull part boxes
 * ({@link VehicleHitboxes}) to both of TaCZ's path searches. {@code @Pseudo} + reflection: no compile dependency on
 * TaCZ, and {@link CompatMixinPlugin} only applies this when TaCZ is loaded.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil")
public abstract class TaczEntityUtilMixin {
    private static Constructor<?> mcheli$resultCtor;
    private static Method mcheli$resultEntity;
    private static Method mcheli$resultPos;

    /** Single-target path (non-piercing rounds): replace TaCZ's nearest hit when a vehicle hull is struck first. */
    @Inject(method = "findEntityOnPath", at = @At("RETURN"), cancellable = true, require = 0)
    private static void mcheli$vehicleHull(Projectile bullet, Vec3 start, Vec3 end, CallbackInfoReturnable<Object> cir) {
        VehicleHitboxes.Hit hit = VehicleHitboxes.nearest(bullet.level(), bullet, start, end, null);
        if (hit == null || !mcheli$reflect()) {
            return;
        }
        Object current = cir.getReturnValue();
        if (current == null || hit.distanceSqr(start) < start.distanceToSqr(mcheli$pos(current))) {
            cir.setReturnValue(mcheli$result(hit));
        }
    }

    /** Piercing path: swap each vehicle's core-box result for its hull hit, and add vehicles struck only by the hull. */
    @Inject(method = "findEntitiesOnPath", at = @At("RETURN"), cancellable = true, require = 0)
    private static void mcheli$vehicleHulls(Projectile bullet, Vec3 start, Vec3 end, CallbackInfoReturnable<List<Object>> cir) {
        List<Object> current = cir.getReturnValue();
        List<AbstractMchVehicle> seen = new ArrayList<>();
        List<Object> out = new ArrayList<>();
        if (current != null) {
            for (Object r : current) {
                if (mcheli$reflect() && mcheli$entity(r) instanceof AbstractMchVehicle) {
                    continue; // re-added below with its hull hit point
                }
                out.add(r);
            }
        }
        // Every vehicle the path crosses (piercing rounds may pass through several).
        Vec3 from = start;
        for (int guard = 0; guard < 8; guard++) {
            VehicleHitboxes.Hit hit = VehicleHitboxes.nearest(bullet.level(), bullet, from, end,
                e -> !seen.contains(e));
            if (hit == null || !mcheli$reflect()) {
                break;
            }
            seen.add(hit.vehicle());
            out.add(mcheli$result(hit));
        }
        if (!seen.isEmpty()) {
            cir.setReturnValue(out);
        }
    }

    private static boolean mcheli$reflect() {
        if (mcheli$resultCtor != null) {
            return true;
        }
        try {
            Class<?> c = Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult");
            mcheli$resultCtor = c.getConstructor(Entity.class, Vec3.class, boolean.class);
            mcheli$resultEntity = c.getMethod("getEntity");
            mcheli$resultPos = c.getMethod("getHitPos");
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Object mcheli$result(VehicleHitboxes.Hit hit) {
        try {
            return mcheli$resultCtor.newInstance(hit.vehicle(), hit.pos(), false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Entity mcheli$entity(Object result) {
        try {
            return (Entity) mcheli$resultEntity.invoke(result);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Vec3 mcheli$pos(Object result) {
        try {
            return (Vec3) mcheli$resultPos.invoke(result);
        } catch (ReflectiveOperationException e) {
            return Vec3.ZERO;
        }
    }
}
