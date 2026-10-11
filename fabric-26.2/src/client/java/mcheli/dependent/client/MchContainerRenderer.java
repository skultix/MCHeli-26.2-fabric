package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchContainer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders a {@link MchContainer} as the bundled {@code models/container} model textured from
 * {@code mcheli:textures/container.png} — the port of {@code MCH_RenderContainer}. Opaque, normally lit, yaw-oriented.
 */
public class MchContainerRenderer extends MchSimpleRenderer<MchContainer> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(MCHeli.MODID, "textures/container.png");

    public MchContainerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.9F;
    }

    @Override
    public void submit(State<MchContainer> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        ModelHandle h = mcheli.dependent.port.MchContentPacks.resources().loadModel("container");
        if (h instanceof MchModel model) {
            MchContainer entity = state.entity;
            pose.pushPose();
            float yaw = Mth.rotLerp(state.partialTick, entity.yRotO, entity.getYRot());
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            int light = state.lightCoords;
            submitGeometry(pose, collector, RenderTypes.entityCutout(TEXTURE), (ps, consumer) ->
                MchModelRenderer.render(model, ps, consumer, light, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255));
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }
}
