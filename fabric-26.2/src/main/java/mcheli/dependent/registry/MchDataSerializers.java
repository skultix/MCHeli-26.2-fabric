package mcheli.dependent.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;

/**
 * Custom synced-entity-data serializers. Vanilla dropped {@code EntityDataSerializers.COMPOUND_TAG} (1.21.9), but the
 * vehicle still syncs two small maps as compound tags (seat → passenger id, weapon → total ammo), so MCHeli registers
 * its own. Call {@link #init} from {@code MCHeli.onInitialize} before any entity type is used.
 */
public final class MchDataSerializers {
    private MchDataSerializers() {}

    /** Server → client only, so the trusted (unbounded) codec is fine. Values are replaced, never mutated in place. */
    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG =
        EntityDataSerializer.forValueType(ByteBufCodecs.TRUSTED_COMPOUND_TAG);

    static {
        FabricEntityDataRegistry.register(MchRegistries.id("compound_tag"), COMPOUND_TAG);
    }

    public static void init() {
        // class init registers the serializers
    }
}
