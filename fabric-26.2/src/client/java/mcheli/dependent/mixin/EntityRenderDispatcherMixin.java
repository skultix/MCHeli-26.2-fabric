package mcheli.dependent.mixin;

import mcheli.dependent.client.MchRiderRenderHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips rendering a rider hidden by its vehicle (NeoForge's cancelled {@code RenderLivingEvent.Pre}). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mcheli$hideRider(E entity, Frustum frustum, double x, double y, double z,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (MchRiderRenderHider.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
