package mcheli.dependent.client.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps TaCZ's scoped gun-model zoom working next to NTM. Both hook {@code Camera.calculateHudFov}: TaCZ with a
 * {@code @ModifyReturnValue} that posts its {@code ViewportEvent.ComputeFov} (the gun-model zoom listens to it), NTM
 * with a cancellable {@code @Inject} that always sets the return value. Whenever NTM's lands first (mixin order follows
 * mod load order) it returns early and TaCZ's never runs, so the scoped gun model does not zoom.
 *
 * <p>{@code TaczNtmHudFovMixin} wraps the call to {@code calculateHudFov} in {@code Camera.update}, which nothing can
 * cancel. A listener on TaCZ's event notes whether TaCZ posted its gun-model event during the call; if it did not, this
 * posts it exactly as TaCZ's hook would ({@code new ComputeFov(renderer, camera, partialTick, fov, false)}). If TaCZ's
 * hook did run, nothing is done, so it never applies twice. Reflection only; if TaCZ's event API changes, this switches
 * itself off. The real fix belongs in NTM ({@code com.hbm.mixin.MixinCamera} should use {@code @ModifyReturnValue}).
 */
public final class TaczHudFov {
    private TaczHudFov() {}

    private static final Logger LOG = LoggerFactory.getLogger("MCHeli");
    private static final String EVENT = "cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent";

    private static boolean initialised;
    private static boolean broken;
    private static Event<Object> fovEvent;
    private static Method post;
    private static Constructor<?> computeFov;
    private static Method getFov;
    /** Set by our listener when TaCZ's gun-model FOV event is posted. */
    private static boolean posted;
    private static int repairs;

    /** How many frames were repaired (TaCZ's hook skipped), for tests. */
    public static int repairs() {
        return repairs;
    }

    /** {@code Camera.calculateHudFov(partialTick)}, with TaCZ's gun-model FOV event posted if NTM skipped it. */
    public static float calculate(Camera camera, float partialTick, Operation<Float> original) {
        if (!initialised) {
            init();
        }
        posted = false;
        float fov = original.call(camera, partialTick);
        if (broken || posted) {
            return fov;
        }
        try {
            GameRenderer renderer = Minecraft.getInstance().gameRenderer;
            Object e = computeFov.newInstance(renderer, camera, (double) partialTick, (double) fov, false);
            post.invoke(fovEvent.invoker(), e);
            if (repairs++ == 0) {
                LOG.info("MCHeli: another mod's FOV hook skipped TaCZ's gun-model zoom; MCHeli applies it instead");
            }
            return (float) (double) getFov.invoke(e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            LOG.warn("MCHeli: TaCZ gun-model FOV workaround disabled", ex);
            return fov;
        }
    }

    @SuppressWarnings("unchecked")
    private static void init() {
        initialised = true;
        try {
            Class<?> viewport = Class.forName(EVENT);
            Class<?> callback = Class.forName(EVENT + "$FovCallback");
            Class<?> compute = Class.forName(EVENT + "$ComputeFov");
            computeFov = compute.getConstructor(GameRenderer.class, Camera.class, double.class, double.class, boolean.class);
            getFov = compute.getMethod("getFOV");
            post = callback.getMethod("post", compute);
            Method usedConfigured = compute.getMethod("usedConfiguredFov");
            fovEvent = (Event<Object>) viewport.getField("FOV").get(null);
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[] {callback}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "post" -> {
                        if (!(boolean) usedConfigured.invoke(args[0])) {
                            posted = true; // the gun-model (HUD) FOV event, not the world one
                        }
                        return null;
                    }
                    case "hashCode" -> { return System.identityHashCode(proxy); }
                    case "equals" -> { return proxy == args[0]; }
                    default -> { return "MCHeli TaCZ gun-model FOV watcher"; }
                }
            });
            fovEvent.register(listener);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            LOG.warn("MCHeli: TaCZ's FOV event not found; gun-model FOV workaround disabled", ex);
        }
    }
}
