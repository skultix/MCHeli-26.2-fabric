package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;

/**
 * Base for MCHeli's small entity renderers (bullet, cartridge, container). 26.x renders in an extract phase and a
 * submit phase; these keep the entity + partial tick in the state and queue their existing draw code as a single
 * custom-geometry node (see {@link MchVehicleRenderState} for the same pattern on the vehicles).
 */
public abstract class MchSimpleRenderer<E extends Entity> extends EntityRenderer<E, MchSimpleRenderer.State<E>> {

    public static class State<E extends Entity> extends EntityRenderState {
        public E entity;
        public float partialTick;
    }

    protected MchSimpleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State<E> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(E entity, State<E> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
    }

    /** Queue {@code draw} into {@code type}'s buffer under the current pose (run later this frame, render thread). */
    protected static void submitGeometry(PoseStack pose, SubmitNodeCollector collector, RenderType type,
                                         BiConsumer<PoseStack, VertexConsumer> draw) {
        collector.submitCustomGeometry(pose, type, (p, consumer) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            draw.accept(ps, consumer);
        });
    }
}
