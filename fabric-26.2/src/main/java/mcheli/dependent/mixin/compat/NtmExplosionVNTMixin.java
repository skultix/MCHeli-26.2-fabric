package mcheli.dependent.mixin.compat;

import mcheli.dependent.compat.NtmCompat;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NTM's own explosion ({@code ExplosionVNT}, which implements vanilla {@link Explosion}) made visible to
 * {@link NtmCompat} while it runs, so a tank it hurts knows how big it is. Applied only when NTM is loaded.
 */
@Pseudo
@Mixin(targets = "com.hbm.explosion.vanillant.ExplosionVNT")
public abstract class NtmExplosionVNTMixin {
    @Inject(method = "explode", at = @At("HEAD"), require = 0)
    private void mcheli$enter(CallbackInfo ci) {
        NtmCompat.enter((Explosion) (Object) this);
    }

    @Inject(method = "explode", at = @At("RETURN"), require = 0)
    private void mcheli$exit(CallbackInfo ci) {
        NtmCompat.exit();
    }
}
