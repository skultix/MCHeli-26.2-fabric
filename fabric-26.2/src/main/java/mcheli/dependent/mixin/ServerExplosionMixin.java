package mcheli.dependent.mixin;

import mcheli.dependent.compat.NtmCompat;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the explosion hurting entities visible to {@link NtmCompat}, which sizes NTM blasts that use vanilla ones. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "hurtEntities", at = @At("HEAD"))
    private void mcheli$enter(CallbackInfo ci) {
        NtmCompat.enter((Explosion) (Object) this);
    }

    @Inject(method = "hurtEntities", at = @At("RETURN"))
    private void mcheli$exit(CallbackInfo ci) {
        NtmCompat.exit();
    }
}
