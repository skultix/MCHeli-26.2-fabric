package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Renders each {@link VehicleSpawnItem}'s inventory/creative icon as the vehicle's actual 3D model — the config-driven
 * "actual icon" (any future vehicle that ships a config + model gets a correct icon with no hand-drawn sprite). The model
 * is fit into the item cell (scaled by its largest dimension, centered); the {@code mcheli:item/vehicle} base model's
 * {@code display} block supplies the viewing angle.
 *
 * <p>26.x: {@code BlockEntityWithoutLevelRenderer} is gone, so this is a vanilla {@link SpecialModelRenderer} of type
 * {@code mcheli:vehicle}, referenced from every vehicle's {@code assets/mcheli/items/<name>.json} client item (a
 * content-pack vehicle's definition is synthesized by {@code MchPackResources}).
 */
public class VehicleItemRenderer implements SpecialModelRenderer<VehicleSpawnItem> {

    public static final Identifier TYPE_ID = MchRegistries.id("vehicle");

    /** Registers the {@code mcheli:vehicle} special-model type (call before resources load, from client init). */
    public static void register() {
        SpecialModelRenderers.ID_MAPPER.put(TYPE_ID, Unbaked.MAP_CODEC);
    }

    /** The JSON-side half: no parameters — the vehicle comes from the rendered stack's item. */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<VehicleSpawnItem> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<VehicleSpawnItem> bake(BakingContext context) {
            return new VehicleItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private final Map<String, MchModel> modelCache = new HashMap<>();
    private final Map<String, Identifier> texCache = new HashMap<>();

    @Override
    public VehicleSpawnItem extractArgument(ItemStack stack) {
        return stack.getItem() instanceof VehicleSpawnItem item ? item : null;
    }

    @Override
    public void submit(VehicleSpawnItem item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
                       boolean foil, int outlineColor) {
        if (item == null) {
            return;
        }
        String path = item.category.dir + "/" + item.configName;
        MchModel model;
        if (this.modelCache.containsKey(path)) {
            model = this.modelCache.get(path); // may be null (negative cache) — don't re-parse a failed icon every frame
        } else {
            ModelHandle h = mcheli.dependent.port.MchContentPacks.resources().loadModel(path);
            model = h instanceof MchModel m ? m : null;
            this.modelCache.put(path, model);
        }
        if (model == null) {
            return;
        }
        float sx = model.maxX - model.minX;
        float sy = model.maxY - model.minY;
        float sz = model.maxZ - model.minZ;
        float max = Math.max(sx, Math.max(sy, sz));
        if (max < 0.01F) {
            max = 1.0F;
        }
        Identifier tex = this.texCache.computeIfAbsent(path,
            p -> Identifier.fromNamespaceAndPath("mcheli", "textures/" + p + ".png"));

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);         // centre of the item cell
        float s = 0.95F / max;                     // fit the largest dimension into the cell
        pose.scale(s, s, s);
        pose.translate(-(model.minX + model.maxX) / 2.0F,   // centre the model geometry on the origin
                       -(model.minY + model.maxY) / 2.0F,
                       -(model.minZ + model.maxZ) / 2.0F);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(tex), (p, consumer) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            MchModelRenderer.render(model, ps, consumer, light, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
        });
        pose.popPose();
    }

    /** The icon fills the unit item cell. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }
}
