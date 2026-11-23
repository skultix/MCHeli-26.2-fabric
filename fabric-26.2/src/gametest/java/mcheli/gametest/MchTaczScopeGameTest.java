package mcheli.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import mcheli.dependent.client.MchGunnerView;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaCZ scopes zoom through {@code Camera.calculateFov} / {@code calculateHudFov} hooks that post its
 * {@code ViewportEvent.ComputeFov}. MCHeli's gunner zoom hooks the same method, and once cancelled it, which skipped
 * TaCZ's hook: scopes did not zoom. This stands in for a scope with a listener on that event (a 4x zoom, world and gun
 * model) and checks that it zooms on foot, that MCHeli's gunner zoom still works, and that the two stack.
 */
public class MchTaczScopeGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mcheli-gametest");
    private static volatile boolean scoped;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            LOG.info("[GAMETEST-SCOPE] TaCZ not installed, skipped");
            return;
        }
        listenLikeAScope();
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode creative @a");
            ctx.runOnClient(mc -> mc.options.fov().set(70));

            // The real thing first: aim a scoped TaCZ rifle (the AWP's built-in scope).
            float[] realAim = aimFor(ctx, sp, "tacz:ai_awp");
            LOG.info("[GAMETEST-SCOPE] real AWP aim: world {} -> {}, gun model {} -> {}", realAim[0], realAim[2],
                realAim[1], realAim[3]);
            check(realAim[2] < realAim[0] * 0.8F, "aiming the AWP must zoom the world in");

            float[] foot = fovs(ctx);
            scoped = true;
            float[] footScoped = fovs(ctx);
            scoped = false;
            LOG.info("[GAMETEST-SCOPE] on foot: world {} -> {}, gun model {} -> {}", foot[0], footScoped[0], foot[1], footScoped[1]);
            check(near(footScoped[0], foot[0] / 4), "a TaCZ scope must narrow the world FOV on foot");
            // With NTM, its cancellable gun-model FOV hook can skip TaCZ's (mixin order); TaczHudFov repairs that.
            LOG.info("[GAMETEST-SCOPE] gun-model FOV repaired by MCHeli in {} frames (0 = TaCZ's own hook ran)",
                (Object) ctx.computeOnClient(mc -> (Integer) mcheli.dependent.client.compat.TaczHudFov.repairs()));
            check(near(footScoped[1], foot[1] / 4), "a TaCZ scope must narrow the gun model FOV on foot");
            check(realAim[3] < realAim[1] * 0.8F, "aiming the AWP must zoom the gun model in");

            // MCHeli's own gunner zoom: the Ka-52's pilot in gunner mode, one zoom step (x2).
            AbstractMchVehicle[] v = {null};
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.level();
                v[0] = MchRegistries.HELI.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
                v[0].setConfigName("ka52");
                v[0].setPos(p.getX() + 3, p.getY(), p.getZ());
                level.addFreshEntity(v[0]);
            });
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                p.startRiding(v[0], true, true);
            });
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> v[0].toggleGunnerMode(s.getPlayerList().getPlayers().get(0)));
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                AbstractMchVehicle cv = (AbstractMchVehicle) mc.player.getVehicle();
                check(cv != null && cv.isSeatGunnerMode(0), "the pilot should be in gunner mode");
                MchGunnerView.stepZoom(cv);
            });
            float[] seat = fovs(ctx);
            check(MchGunnerView.currentZoom() == 2.0F, "one zoom step should be x2, is " + MchGunnerView.currentZoom());
            scoped = true;
            float[] both = fovs(ctx);
            scoped = false;
            LOG.info("[GAMETEST-SCOPE] Ka-52 gunner x2: world {}; with the scope too: {}", seat[0], both[0]);
            check(near(seat[0], foot[0] / 2), "MCHeli's gunner zoom must still halve the FOV: " + foot[0] + " -> " + seat[0] + " (" + ctx.computeOnClient(MchTaczScopeGameTest::fovState) + ")");
            check(near(both[0], foot[0] / 8), "the gunner zoom and a scope must stack");
            LOG.info("[GAMETEST-SCOPE] PASS");
        }
    }

    /** {world, model} FOV holding {@code gun}, then {world, model} while aiming it (then stops aiming). */
    private static float[] aimFor(ClientGameTestContext ctx, TestSingleplayerContext sp, String gun) {
        sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0)
            .setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, MchTaczGameTest.buildGun(s, gun)));
        ctx.waitTicks(40);
        float[] idle = fovs(ctx);
        ctx.getInput().holdKey(aimKey()); // the player holds TaCZ's aim key (right mouse)
        ctx.waitTicks(40);
        float[] aimed = fovs(ctx);
        ctx.runOnClient(mc -> LOG.info("[GAMETEST-SCOPE] aim state: {}", aimState(mc)));
        ctx.getInput().releaseKey(aimKey());
        ctx.waitTicks(100);
        sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0)
            .setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY));
        return new float[] {idle[0], idle[1], aimed[0], aimed[1]};
    }

    private static String aimState(net.minecraft.client.Minecraft mc) {
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Object o = op.getMethod("fromLocalPlayer", net.minecraft.client.player.LocalPlayer.class).invoke(null, mc.player);
            Class<?> kir = Class.forName("com.tacz.guns.api.client.other.KeepingItemRenderer");
            Object r = kir.getMethod("getRenderer").invoke(null);
            Object item = kir.getMethod("getCurrentItem").invoke(r);
            Class<?> sop = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Object so = sop.getMethod("fromLivingEntity", net.minecraft.world.entity.LivingEntity.class).invoke(null, mc.player);
            return "isAim=" + op.getMethod("isAim").invoke(o) + " progress=" + op.getMethod("getClientAimingProgress", float.class)
                .invoke(o, 1.0F) + " synProgress=" + sop.getMethod("getSynAimingProgress").invoke(so) + " keepingItem=" + item
                + " mainHand=" + mc.player.getMainHandItem() + " camEntity=" + mc.gameRenderer.mainCamera().entity()
                + " vehicle=" + mc.player.getVehicle();
        } catch (ReflectiveOperationException e) {
            return "?" + e;
        }
    }

    private static net.minecraft.client.KeyMapping aimKey() {
        try {
            return (net.minecraft.client.KeyMapping) Class.forName("com.tacz.guns.client.input.AimKey").getField("AIM_KEY").get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Settled world and HUD (gun model) FOV, read off the camera after a few frames. */
    private static float[] fovs(ClientGameTestContext ctx) {
        ctx.waitTicks(10);
        return ctx.computeOnClient(mc -> {
            Camera cam = mc.gameRenderer.mainCamera();
            try {
                Field hud = Camera.class.getDeclaredField("hudFov");
                hud.setAccessible(true);
                return new float[] {cam.getFov(), hud.getFloat(cam)};
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    /** A 4x "scope" on TaCZ's own FOV event, the one its aiming listens to (world and gun model alike). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void listenLikeAScope() {
        try {
            Class<?> viewport = Class.forName("cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent");
            Class<?> callback = Class.forName("cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent$FovCallback");
            Class<?> compute = Class.forName("cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent$ComputeFov");
            Method get = compute.getMethod("getFOV");
            Method set = compute.getMethod("setFOV", double.class);
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[] {callback}, (proxy, m, args) -> {
                if (m.getName().equals("post")) {
                    if (scoped) {
                        set.invoke(args[0], (double) get.invoke(args[0]) / 4.0);
                    }
                    return null;
                }
                return switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "scope";
                };
            });
            ((Event) viewport.getField("FOV").get(null)).register(listener);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String fovState(Minecraft mc) {
        return mc.player == null ? "" : "fovEffect=" + mc.options.fovEffectScale().get() + " flying="
            + mc.player.getAbilities().flying + " zoom=" + MchGunnerView.currentZoom() + " vehicle=" + mc.player.getVehicle();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0F; // TaCZ eases its FOV (SecondOrderDynamics), so allow a degree
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }
}
