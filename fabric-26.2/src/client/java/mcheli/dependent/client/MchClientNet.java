package mcheli.dependent.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every MCHeli client-to-server packet goes through here. {@link ClientPlayNetworking#send} throws when there is no
 * play connection, and the vehicle code runs on client ticks that keep firing while a world is being left (a player
 * disconnecting from the seat crashed the client this way), so a send with no connection is dropped instead.
 */
public final class MchClientNet {
    private MchClientNet() {}

    public static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().player != null) {
            ClientPlayNetworking.send(payload);
        }
    }
}
