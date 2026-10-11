package mcheli.dependent.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Soft integration with TaCZ (Refabricated), putting its guns on MCHeli's damage scale against vehicles: explosive
 * ammo (RPG-7, M320, HE ammo mod) does its designed blast damage, and plain bullets do half damage to unarmoured
 * vehicles and none to armoured ones ({@link #ARMORED_MIN_DAMAGE}). No compile dependency — TaCZ is detected and read by reflection, so MCHeli runs
 * unchanged without it.
 *
 * <p>Why: TaCZ gun packs give explosive ammo a designed blast damage ({@code bullet.explosion.damage}, e.g. RPG-7 =
 * 120), but the Fabric port's {@code ExplodeUtil} only passes the explosion RADIUS to a vanilla explosion (radius 3 ->
 * at most 43 at the centre). After MCHeli's armour formula (×armorDamageFactor, −armorMinDamage) a tank barely notices.
 * Here the designed value is applied instead:
 * <ul>
 *   <li><b>Direct hit</b> — the rocket's bullet damage + its designed explosion damage, once per rocket (TaCZ deals a
 *       hit as two {@code hurt} calls, a normal and an armour-ignoring part; only the first positive one counts).</li>
 *   <li><b>That rocket's own vanilla blast</b> is then ignored on the same vehicle (its damage is already counted).</li>
 *   <li><b>Near miss</b> — the vanilla blast of an explosive TaCZ round is rescaled so its centre value equals the
 *       designed explosion damage (vanilla's distance/exposure falloff is kept). Only while TaCZ's {@code ExplodeUtil}
 *       uses a vanilla explosion (the 26.2 port); upstream's own {@code ProjectileExplosion} already applies the
 *       designed damage, so it is left alone.</li>
 * </ul>
 * The result still goes through the vehicle's normal armour formula and per-part armour.
 */
public final class TaczCompat {
    private TaczCompat() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");
    private static final String BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";

    /** Plain (non-explosive) TaCZ bullets deal this fraction of their damage to unarmoured vehicles: a vehicle's HP is
     *  sized for MCHeli's own weapons, so full rifle damage (an AWP does 42) downs an AH-64 in five shots. */
    public static final float SMALL_ARMS_FACTOR = 0.5F;
    /** A vehicle whose config {@code ArmorMinDamage} is at least this is ARMOURED (the real tanks: m1a2, t-90, merkava,
     *  kv-2, ...; 9..12): plain bullets do nothing to it — only explosive ammo and explosions do. Unarmoured "tanks"
     *  (growler, bnr32, mxtmv) and everything else stay vulnerable to gunfire. */
    public static final float ARMORED_MIN_DAMAGE = 5.0F;

    private static Class<?> bulletClass;
    private static Field explosionField;
    private static Field explosionDamageField;
    private static Field explosionRadiusField;
    private static boolean reflectionFailed;
    /** True when TaCZ's blasts drop the designed damage (the 26.2 port's ExplodeUtil uses a vanilla explosion sized by
     *  radius only). Upstream ExplodeUtil uses its own ProjectileExplosion with the designed damage, which must not be
     *  rescaled again. */
    private static boolean vanillaBlasts;

    /** Per-vehicle memory of the last rocket that hit it directly (so its split hit + own blast count once). */
    public static final class HitMemory {
        int rocketId = -1;
    }

    /**
     * The damage a vehicle should take from {@code src}, or the incoming {@code amount} unchanged when the source is
     * not explosive TaCZ ammo. Returns 0 for a hit that was already counted.
     */
    public static float adjust(HitMemory memory, DamageSource src, float amount, float armorMinDamage) {
        if (!LOADED) {
            return amount;
        }
        Entity direct = src.getDirectEntity();
        if (direct == null || !isBullet(direct)) {
            return amount;
        }
        if (!isExplosive(direct)) {
            LOG.info("[TACZ-DEBUG] plain bullet hurt amount={} armorMin={} src={}", amount, armorMinDamage, src.getMsgId());
            // Plain small-arms fire: armoured vehicles shrug it off; everything else takes reduced damage.
            if (src.is(DamageTypeTags.IS_EXPLOSION)) {
                return amount;
            }
            return armorMinDamage >= ARMORED_MIN_DAMAGE ? 0.0F : amount * SMALL_ARMS_FACTOR;
        }
        float designed = readFloat(explosionDamageField, direct);
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            if (memory.rocketId == direct.getId()) {
                return 0.0F; // this rocket already hit the vehicle directly; its blast is part of that damage
            }
            if (!vanillaBlasts) {
                return amount; // TaCZ's own explosion already carries the designed damage
            }
            float radius = Math.max(0.5F, readFloat(explosionRadiusField, direct));
            float vanillaPeak = 7.0F * 2.0F * radius + 1.0F; // vanilla ServerExplosion damage at distance 0, full exposure
            return designed > 0.0F ? amount * (designed / vanillaPeak) : amount;
        }
        if (memory.rocketId == direct.getId() || amount <= 0.0F) {
            return 0.0F; // the second (armour-ignoring) half of the same TaCZ hit, or an empty part
        }
        memory.rocketId = direct.getId();
        return amount + designed;
    }

    private static boolean isBullet(Entity e) {
        if (reflectionFailed || !"tacz".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) {
            return false;
        }
        if (bulletClass == null && !initReflection()) {
            return false;
        }
        return bulletClass.isInstance(e);
    }

    /** Whether a TaCZ bullet is explosive ammo (RPG/grenade rounds, or any gun loaded with the HE ammo mod). */
    private static boolean isExplosive(Entity bullet) {
        try {
            return explosionField.getBoolean(bullet);
        } catch (IllegalAccessException ex) {
            return false;
        }
    }

    private static boolean initReflection() {
        try {
            Class<?> c = Class.forName(BULLET_CLASS);
            explosionField = accessible(c.getDeclaredField("explosion"));
            explosionDamageField = accessible(c.getDeclaredField("explosionDamage"));
            explosionRadiusField = accessible(c.getDeclaredField("explosionRadius"));
            vanillaBlasts = !classReferences("com/tacz/guns/util/ExplodeUtil.class", "ProjectileExplosion");
            bulletClass = c;
            LOG.info("MCHeli: TaCZ detected — explosive TaCZ ammo uses its designed explosion damage against vehicles"
                + " (TaCZ blasts are {})", vanillaBlasts ? "vanilla, radius-only: rescaling them" : "TaCZ's own: left as-is");
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reflectionFailed = true;
            LOG.warn("MCHeli: TaCZ is loaded but its bullet entity changed shape ({}); TaCZ damage scaling disabled",
                ex.toString());
            return false;
        }
    }

    /** Whether a class file's constant pool mentions {@code name} (a cheap "which implementation is this?" probe). */
    private static boolean classReferences(String resource, String name) {
        try (var in = TaczCompat.class.getClassLoader().getResourceAsStream(resource)) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(name);
        } catch (java.io.IOException ex) {
            return false;
        }
    }

    private static Field accessible(Field f) {
        f.setAccessible(true);
        return f;
    }

    private static float readFloat(Field f, Entity e) {
        try {
            return f.getFloat(e);
        } catch (IllegalAccessException ex) {
            return 0.0F;
        }
    }
}
