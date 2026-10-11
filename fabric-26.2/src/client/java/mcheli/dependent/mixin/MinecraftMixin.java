package mcheli.dependent.mixin;

import mcheli.dependent.client.MchClientRotation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code RenderFrameEvent.Pre}: once per frame, after the accumulated mouse turn is applied and before the
 * frame renders, run the vehicle rotation solver + rider weld so the camera sees this frame's hull orientation.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public abstract DeltaTracker getDeltaTracker();

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
    private void mcheli$beforeRenderFrame(boolean advanceGameTime, CallbackInfo ci) {
        MchClientRotation.onRenderFrame(getDeltaTracker());
    }
}
