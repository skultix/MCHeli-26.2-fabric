package mcheli.dependent.control;

import io.netty.buffer.ByteBuf;
import mcheli.MCHeli;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The port's FIRST clientbound payload — the RWR (radar-warning receiver) cockpit tone for a flare-equipped aircraft's
 * crew: {@link #LOCKED} ("you are being locked", reference {@code locked} sound) and {@link #ALERT} ("a guided missile
 * is inbound", reference {@code alert} sound). The server only sends it to seat-0/1 crew of a vehicle whose config has
 * flares (reference {@code MCH_MissileDetector} gates on {@code haveFlare()}); the client plays the tone with a small
 * cooldown.
 *
 * <p>Side-safety: the receiver ({@code MchRwrClient.play}) is registered from the client entrypoint only
 * ({@code mcheli.MCHeliClient}); a dedicated server registers the payload TYPE but never links the client class.
 */
public record ClientboundRwrPayload(byte kind) implements CustomPacketPayload {

    /** Someone is building a missile lock on you. */
    public static final byte LOCKED = 0;
    /** A guided missile is homing on you right now. */
    public static final byte ALERT = 1;

    public static final Type<ClientboundRwrPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MCHeli.MODID, "rwr"));

    public static final StreamCodec<ByteBuf, ClientboundRwrPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BYTE, ClientboundRwrPayload::kind,
            ClientboundRwrPayload::new);

    @Override
    public Type<ClientboundRwrPayload> type() {
        return TYPE;
    }
}
