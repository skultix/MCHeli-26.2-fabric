package mcheli.dependent.mixin;

import java.util.function.Consumer;
import mcheli.dependent.port.MchPackResourcesFinder;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces NeoForge's {@code AddPackFindersEvent}: after vanilla lists its built-in packs (the client's
 * {@code ClientPackSource} and the server's {@code ServerPacksSource} both extend {@link BuiltInPackSource}), append
 * MCHeli's content-pack resource packs (client) and the generated vehicle-recipe datapack (server).
 */
@Mixin(BuiltInPackSource.class)
public abstract class BuiltInPackSourceMixin {
    @Shadow @Final private PackType packType;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void mcheli$addPacks(Consumer<Pack> consumer, CallbackInfo ci) {
        MchPackResourcesFinder.loadPacks(this.packType, consumer);
    }
}
