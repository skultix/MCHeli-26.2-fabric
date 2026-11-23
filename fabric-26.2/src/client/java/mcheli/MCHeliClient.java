package mcheli;

import mcheli.dependent.client.MchClientEvents;
import mcheli.dependent.client.MchClientInput;
import mcheli.dependent.client.MchClientRotation;
import mcheli.dependent.client.MchClientSound;
import mcheli.dependent.client.MchGunnerView;
import mcheli.dependent.client.MchKeybinds;
import mcheli.dependent.client.MchRwrClient;
import mcheli.dependent.client.hud.MchHudOverlay;
import mcheli.dependent.client.hud.MchWrenchOverlay;
import mcheli.dependent.control.ClientboundRwrPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric 26.2 client entrypoint — every NeoForge {@code Dist.CLIENT} subscriber, registered explicitly. (The rider
 * camera, gunner zoom, per-frame rotation and rider hiding are client mixins; see {@code mcheli.mixins.json}.)
 */
public class MCHeliClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MchClientEvents.register();   // renderers, riding-GUI screen, particle providers
        mcheli.dependent.client.VehicleItemRenderer.register(); // the mcheli:vehicle special item model (3D icons)
        MchKeybinds.register();
        MchClientInput.register();
        MchClientRotation.register();
        MchGunnerView.register();
        MchClientSound.register();
        MchHudOverlay.register();
        MchWrenchOverlay.register();
        // The RWR cockpit warning tone (the only clientbound payload; its TYPE is registered in common code).
        ClientPlayNetworking.registerGlobalReceiver(ClientboundRwrPayload.TYPE,
            (payload, context) -> MchRwrClient.play(payload.kind()));
    }
}
