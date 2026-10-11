package mcheli.dependent.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges 26.x's final {@code Entity.moveOrInterpolateTo} back to MCHeli's own client lerp. The NeoForge-era entities
 * overrode {@code lerpTo(x, y, z, yRot, xRot, steps)}; vanilla now routes every server position update through
 * {@link Entity#getInterpolation()}, so each entity returns one of these, which forwards the update to its old lerp
 * code. It also remembers the last target, because vanilla reads {@link #position()}/{@link #yRot()}/{@link #xRot()}
 * from the handler to fill in a field an update omits.
 */
public final class MchInterpolation extends InterpolationHandler {
    /** The entity's lerp entry point (the old {@code lerpTo} body). */
    @FunctionalInterface
    public interface Target {
        void lerpTo(double x, double y, double z, float yRot, float xRot, int steps);
    }

    private final Entity entity;
    private final Target target;
    private Vec3 lastPos;
    private float lastYRot;
    private float lastXRot;
    private boolean hasTarget;

    public MchInterpolation(Entity entity, Target target) {
        super(entity);
        this.entity = entity;
        this.target = target;
    }

    @Override
    public void interpolateTo(Vec3 pos, float yRot, float xRot) {
        this.lastPos = pos;
        this.lastYRot = yRot;
        this.lastXRot = xRot;
        this.hasTarget = true;
        this.target.lerpTo(pos.x, pos.y, pos.z, yRot, xRot, InterpolationHandler.DEFAULT_INTERPOLATION_STEPS);
    }

    @Override public Vec3 position() { return this.hasTarget ? this.lastPos : this.entity.position(); }
    @Override public float yRot() { return this.hasTarget ? this.lastYRot : this.entity.getYRot(); }
    @Override public float xRot() { return this.hasTarget ? this.lastXRot : this.entity.getXRot(); }
    // MCHeli advances its own lerp in tick(); vanilla's per-tick interpolate() must not move the entity a second time.
    @Override public boolean hasActiveInterpolation() { return false; }
    @Override public void interpolate() {}
}
