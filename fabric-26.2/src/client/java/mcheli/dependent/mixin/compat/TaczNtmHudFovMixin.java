package mcheli.dependent.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mcheli.dependent.client.compat.TaczHudFov;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applied only with both TaCZ and NTM loaded: NTM's cancellable hook on {@code calculateHudFov} can skip TaCZ's
 * gun-model zoom, so the call is wrapped where it cannot be cancelled. See {@link TaczHudFov}.
 */
@Mixin(Camera.class)
public abstract class TaczNtmHudFovMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;calculateHudFov(F)F"))
    private float mcheli$taczHudFov(Camera self, float partialTick, Operation<Float> original) {
        return TaczHudFov.calculate(self, partialTick, original);
    }
}
