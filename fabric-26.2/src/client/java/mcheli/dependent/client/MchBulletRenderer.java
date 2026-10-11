package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Renders a {@link MchBullet} as its <b>configured bullet model</b>: the {@code .mqo}/{@code .obj} named by the firing
 * weapon's {@code ModelBullet=} (or the per-type default — bullet / rocket / aamissile / …), loaded through the shared
 * {@link NeoResourceSource} cache and textured from {@code mcheli:textures/bullets/<name>.png}. The model is oriented
 * along the bullet's flight ({@code -yaw} then {@code +pitch}, no scale — matching the reference {@code MCH_RenderBullet}
 * and the mqo parser's built-in /100 scale) and tinted by the weapon's colour (opaque white = untinted).
 *
 * <p>If the model can't be resolved (empty/unknown name, or a parse miss) it falls back to a small full-bright tracer
 * cube so the projectile is never invisible.
 */
public class MchBulletRenderer extends MchSimpleRenderer<MchBullet> {
    private static final Identifier TRACER_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/block/glowstone.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final ModelPart tracer;

    public MchBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "tracer", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        this.tracer = LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    @Override
    public void submit(State<MchBullet> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        MchBullet entity = state.entity;
        String name = entity.bulletModelName();
        MchModel model = resolve(name);
        pose.pushPose();
        // Orient the model +Z along the flight path (yaw/pitch are kept current from the motion each tick).
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        if (model != null) {
            int color = entity.bulletColor();
            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            Identifier tex = Identifier.fromNamespaceAndPath(MCHeli.MODID, "textures/bullets/" + name + ".png");
            submitGeometry(pose, collector, RenderTypes.entityCutout(tex), (ps, consumer) ->
                MchModelRenderer.render(model, ps, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, a));
        } else {
            // Fallback tracer: a tiny full-bright cube so an unresolved model still reads as a streak.
            pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            submitGeometry(pose, collector, RenderTypes.entityCutout(TRACER_TEXTURE), (ps, consumer) ->
                this.tracer.render(ps, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        }
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    private static MchModel resolve(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        ModelHandle h = mcheli.dependent.port.MchContentPacks.resources().loadModel("bullets/" + name);
        return h instanceof MchModel m ? m : null;
    }
}
