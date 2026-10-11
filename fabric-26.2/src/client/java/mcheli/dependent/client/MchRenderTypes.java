package mcheli.dependent.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render types vanilla 26.x no longer offers. {@code RenderType.entityTranslucentCull} was removed from
 * {@code RenderTypes}, but its pipeline ({@link RenderPipelines#ENTITY_TRANSLUCENT_CULL}) remains; MCHeli draws every
 * vehicle in that single alpha-blended, back-face-culled, depth-writing pass (the reference's
 * {@code W_Render.setCommonRenderParam}), so cockpit glass is see-through purely from the texture alpha.
 * {@code RenderType.create} is opened by {@code mcheli.classtweaker}.
 */
public final class MchRenderTypes {
    private MchRenderTypes() {}

    private static final Map<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = new HashMap<>();

    /** Same setup as vanilla {@code RenderTypes.entityTranslucent(tex, true)}, on the CULL pipeline. */
    public static RenderType entityTranslucentCull(Identifier texture) {
        return ENTITY_TRANSLUCENT_CULL.computeIfAbsent(texture, tex -> RenderType.create("mcheli_entity_translucent_cull",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()));
    }
}
