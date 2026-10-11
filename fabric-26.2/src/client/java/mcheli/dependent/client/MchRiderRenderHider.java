package mcheli.dependent.client;

import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.world.entity.Entity;

/**
 * Hides the rider of a vehicle whose config hides it (an enclosed cockpit / tank hull — {@link
 * AbstractMchVehicle#hidesRider()}). NeoForge cancelled {@code RenderLivingEvent.Pre}; Fabric has no cancellable
 * living-render event, so {@code EntityRenderDispatcherMixin} asks {@link #shouldHide} from
 * {@code EntityRenderDispatcher.shouldRender}.
 */
public final class MchRiderRenderHider {
    private MchRiderRenderHider() {}

    public static boolean shouldHide(Entity entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity
            && entity.getVehicle() instanceof AbstractMchVehicle vehicle && vehicle.hidesRider();
    }
}
