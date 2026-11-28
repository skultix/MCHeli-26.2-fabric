package mcheli.dependent.mixin.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerAnimationLib's {@code AdjustmentModifier} only learns its frame data ({@code data}) from {@code tick} or
 * {@code setupAnim}, but TaCZ starts its third-person gun animations from inside {@code HumanoidModel.setupAnim}, so a
 * player entity that has just been created (a player joining, or every player around you being re-sent after you
 * respawn) can reach {@code get3DTransform} before either ran: {@code this.data} is null and the client crashes
 * ("Rendering entity in world"). Give such a modifier a neutral frame instead. {@code @Pseudo} + reflection: no
 * compile dependency, and {@link CompatMixinPlugin} only applies this when PlayerAnimationLib is loaded.
 */
@Pseudo
@Mixin(targets = "com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier")
public abstract class PalAdjustmentModifierMixin {
    private static Field mcheli$data;
    private static Constructor<?> mcheli$dataCtor;
    private static boolean mcheli$failed;

    @Inject(method = "get3DTransform", at = @At("HEAD"), require = 0)
    private void mcheli$ensureData(CallbackInfo ci) {
        if (mcheli$failed) {
            return;
        }
        try {
            if (mcheli$data == null) {
                Field f = this.getClass().getClassLoader()
                    .loadClass("com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier")
                    .getDeclaredField("data");
                f.setAccessible(true);
                mcheli$dataCtor = f.getType().getConstructor(float.class, float.class, boolean.class);
                mcheli$data = f;
            }
            if (mcheli$data.get(this) == null) {
                float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                mcheli$data.set(this, mcheli$dataCtor.newInstance(0.0F, partialTick, false));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            mcheli$failed = true; // a different PlayerAnimationLib layout: leave it alone
        }
    }
}
