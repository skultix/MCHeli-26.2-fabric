package mcheli.dependent.client.hud;

import mcheli.MCHeli;
import mcheli.agnostic.hud.MCH_Hud;
import mcheli.agnostic.hud.MCH_HudManager;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Registers the MCHeli cockpit HUD as a HUD element after the crosshair. It runs only while the in-game HUD renders and, when the local player is
 * riding an {@link AbstractMchVehicle}, resolves that vehicle's pilot HUD config, builds a {@link MchHudVarState} from
 * the live entity, and draws it through {@link NeoHudRenderer}. Everything shown is config-driven from the vehicle's
 * {@code HUD =} list + the {@code hud/*.txt} definitions.
 */
public final class MchHudOverlay {
    private MchHudOverlay() {}
    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath(MCHeli.MODID, "vehicle_hud"), MchHudOverlay::render);
        // NeoForge cancelled RenderGuiLayerEvent.Pre for the crosshair layer; Fabric wraps the vanilla element instead.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, deltaTracker) -> {
            if (!hidesVanillaCrosshair()) {
                vanilla.extractRenderState(g, deltaTracker);
            }
        });
    }

    /**
     * Suppress the vanilla crosshair while piloting an MCHeli vehicle: the cockpit HUD draws its own configured
     * reticle (the {@code common_pilot} crosshair), so the vanilla white {@code +} would just be a stray, unconfigured
     * element sitting in its centre. This mirrors the reference, which renders its HUD in place of the vanilla one.
     */
    private static boolean hidesVanillaCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && mc.player.getVehicle() instanceof AbstractMchVehicle vehicle
            && vehicle.hudName(Math.max(0, vehicle.seatIndexOf(mc.player))) != null;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }
        Entity ridden = mc.player.getVehicle();
        if (!(ridden instanceof AbstractMchVehicle vehicle)) {
            return;
        }
        // Draw the HUD for the seat the LOCAL player actually occupies — a gunner gets their gunner HUD (heli_gnr /
        // gunner / mbt_gnr …), a pilot the pilot HUD; the per-seat names come from the vehicle's HUD = config list.
        int seat = Math.max(0, vehicle.seatIndexOf(mc.player));
        String hudName = vehicle.hudName(seat);
        if (hudName == null) {
            return;
        }
        MCH_Hud hud = MCH_HudManager.get(hudName);
        if (hud == null || hud.isEmpty()) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false); // smooths the radar sweep line
        MchHudVarState state = new MchHudVarState(vehicle, mc.player, g.guiWidth(), g.guiHeight(), partialTick);
        hud.draw(new NeoHudRenderer(g, mc.font), state, MCH_HudManager::get);
    }
}
