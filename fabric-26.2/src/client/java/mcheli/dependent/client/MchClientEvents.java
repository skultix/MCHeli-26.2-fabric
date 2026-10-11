package mcheli.dependent.client;

import mcheli.dependent.client.particle.MuzzleFxParticle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client-side registration of the renderers, the riding-GUI screen and the particle providers — the port of the
 * NeoForge {@code EntityRenderersEvent}/{@code RegisterMenuScreensEvent}/{@code RegisterParticleProvidersEvent}
 * subscribers. Called from {@link mcheli.MCHeliClient}; lives in the client source set, so a dedicated server never
 * loads it (or the renderers it references).
 */
public final class MchClientEvents {
    private MchClientEvents() {}

    public static void register() {
        EntityRendererRegistry.register(MchRegistries.VEHICLE.get(), MchGroundVehicleRenderer::new);
        EntityRendererRegistry.register(MchRegistries.HELI.get(), MchHelicopterRenderer::new);
        EntityRendererRegistry.register(MchRegistries.PLANE.get(), MchPlaneRenderer::new);
        EntityRendererRegistry.register(MchRegistries.TANK.get(), MchTankRenderer::new);
        EntityRendererRegistry.register(MchRegistries.DEMO_BULLET.get(), MchBulletRenderer::new);
        EntityRendererRegistry.register(MchRegistries.CARTRIDGE.get(), MchCartridgeRenderer::new);
        EntityRendererRegistry.register(MchRegistries.CONTAINER.get(), MchContainerRenderer::new);
        EntityRendererRegistry.register(MchRegistries.FLARE.get(), MchFlareRenderer::new);

        // The riding GUI (fuel slots + reload) — bound to the menu type registered in MchRegistries.
        MenuScreens.register(MchRegistries.VEHICLE_MENU.get(), mcheli.dependent.client.screen.MchVehicleScreen::new);

        ParticleProviderRegistry particles = ParticleProviderRegistry.getInstance();
        particles.register(MchRegistries.WEAPON_FX.get(), MuzzleFxParticle.Provider::new);
        particles.register(MchRegistries.EXPLODE_FX.get(), mcheli.dependent.client.particle.MchExplodeParticle.Provider::new);
        particles.register(MchRegistries.SMOKE_FX.get(), mcheli.dependent.client.particle.MchSmokeParticle.Provider::new);
    }
}
