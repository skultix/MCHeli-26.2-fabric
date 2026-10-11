package mcheli.dependent.control;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the MCHeli network payloads: every payload TYPE (both directions, on both sides) plus the server-side
 * receivers. The one clientbound payload's receiver ({@link ClientboundRwrPayload}) is registered client-only from
 * {@code mcheli.MCHeliClient}. Called from {@link mcheli.MCHeli#onInitialize}.
 */
public final class MchControlNetwork {
    private MchControlNetwork() {}

    public static void register() {
        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(ServerboundControlPayload.TYPE, ServerboundControlPayload.STREAM_CODEC);
        c2s.register(ServerboundRotationPayload.TYPE, ServerboundRotationPayload.STREAM_CODEC);
        c2s.register(ServerboundWeaponSwitchPayload.TYPE, ServerboundWeaponSwitchPayload.STREAM_CODEC);
        c2s.register(ServerboundVehicleGuiPayload.TYPE, ServerboundVehicleGuiPayload.STREAM_CODEC);
        c2s.register(ServerboundSeatSwitchPayload.TYPE, ServerboundSeatSwitchPayload.STREAM_CODEC);
        c2s.register(ServerboundGunnerModePayload.TYPE, ServerboundGunnerModePayload.STREAM_CODEC);
        c2s.register(ServerboundFoldBladePayload.TYPE, ServerboundFoldBladePayload.STREAM_CODEC);
        c2s.register(ServerboundUseFlarePayload.TYPE, ServerboundUseFlarePayload.STREAM_CODEC);
        c2s.register(ServerboundVtolPayload.TYPE, ServerboundVtolPayload.STREAM_CODEC);
        c2s.register(ServerboundNotifyLockPayload.TYPE, ServerboundNotifyLockPayload.STREAM_CODEC);
        // The port's first CLIENTBOUND payload — the RWR cockpit warning tone.
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRwrPayload.TYPE, ClientboundRwrPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ServerboundControlPayload.TYPE, ServerboundControlPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRotationPayload.TYPE, ServerboundRotationPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundWeaponSwitchPayload.TYPE, ServerboundWeaponSwitchPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundVehicleGuiPayload.TYPE, ServerboundVehicleGuiPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSeatSwitchPayload.TYPE, ServerboundSeatSwitchPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundGunnerModePayload.TYPE, ServerboundGunnerModePayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundFoldBladePayload.TYPE, ServerboundFoldBladePayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundUseFlarePayload.TYPE, ServerboundUseFlarePayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundVtolPayload.TYPE, ServerboundVtolPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundNotifyLockPayload.TYPE, ServerboundNotifyLockPayload::handle);
    }
}
