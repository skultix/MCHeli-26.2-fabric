package mcheli.dependent.compat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import mcheli.agnostic.weapon.MCH_DamageSettings;
import mcheli.dependent.MchDamageConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Explosion;

/**
 * NTM (HBM Nuclear Tech) explosions against MCHeli tanks, set by {@code ntm} in {@code config/mcheli-damage.json}:
 * <ul>
 *   <li>a nuclear blast ({@code hbm:nuclear_blast}: every NTM nuke, mini-nukes included) destroys the tank;</li>
 *   <li>any other NTM explosion of at least {@code destroyTanksAtExplosionSize} destroys it, except NTM's TNT, C4,
 *       semtex and dynamite, which keep their normal damage; EMPs do no damage at all in NTM;</li>
 *   <li>the anti-tank mine ({@code hbm:mine_he}) does exactly {@code antiTankMineDamage}, past the armour.</li>
 * </ul>
 * Explosions from other mods (and vanilla) are untouched.
 *
 * <p>NTM has no compile dependency. An explosion's size is known while it hurts entities: {@code ServerExplosionMixin}
 * (vanilla explosions, which NTM also uses) and {@code NtmExplosionVNTMixin} (NTM's own) push it here. Whether NTM
 * caused it, and which NTM code, is read off the call stack, which only happens when a tank takes explosion damage.
 */
public final class NtmCompat {
    private NtmCompat() {}

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("hbm");
    private static final ResourceKey<DamageType> NUCLEAR = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath("hbm", "nuclear_blast"));
    private static final ResourceKey<DamageType> ELECTRIC = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath("hbm", "electric"));
    private static final String AT_MINE = "com.hbm.blocks.bomb.BlockLandmineHE";
    /** NTM's TNT family: its blocks blow up through BlockTNTBase.vanillaBlast, their primed form is EntityTntNtm. */
    private static final List<String> TNT = List.of("com.hbm.blocks.bomb.BlockTNTBase", "com.hbm.entity.item.EntityTntNtm");

    /** NTM's nuclear explosions (the classes, and the prefixes of their helpers' names). */
    private static final List<String> NUKE_CODE = List.of("com.hbm.explosion.ExplosionNuke",
        "com.hbm.explosion.ExplosionBalefire", "com.hbm.entity.logic.EntityNukeExplosion", "com.hbm.entity.logic.EntityBalefire");

    private static final ThreadLocal<Deque<Explosion>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    /** An explosion starts hurting entities (called by the mixins). */
    public static void enter(Explosion explosion) {
        if (LOADED) {
            CURRENT.get().push(explosion);
        }
    }

    /** ...and is done. */
    public static void exit() {
        if (LOADED) {
            Deque<Explosion> d = CURRENT.get();
            if (!d.isEmpty()) {
                d.pop();
            }
        }
    }

    /** What an NTM explosion does to a tank. */
    public sealed interface Effect permits Normal, Destroy, Fixed {}

    public record Normal() implements Effect {}

    public record Destroy() implements Effect {}

    public record Fixed(float damage) implements Effect {}

    private static final Effect NORMAL = new Normal();
    private static final Effect DESTROY = new Destroy();

    /** How {@code src} affects a tank. */
    public static Effect forTank(DamageSource src) {
        if (!LOADED) {
            return NORMAL;
        }
        MCH_DamageSettings.Ntm cfg = MchDamageConfig.settings().ntm;
        if (src.is(NUCLEAR)) {
            return cfg.nuclearDestroysTanks ? DESTROY : NORMAL;
        }
        if (src.is(ELECTRIC)) {
            return NORMAL;
        }
        List<String> ntmFrames = StackWalker.getInstance().walk(s -> s.map(StackWalker.StackFrame::getClassName)
            .filter(n -> n.startsWith("com.hbm.")).distinct().toList());
        if (ntmFrames.isEmpty()) {
            return NORMAL; // not NTM's explosion
        }
        // Some nukes hurt with a plain explosion source (ExplosionNukeGeneric.dealDamage): know them by their code.
        if (ntmFrames.stream().anyMatch(n -> NUKE_CODE.stream().anyMatch(n::startsWith))) {
            return cfg.nuclearDestroysTanks ? DESTROY : NORMAL;
        }
        Deque<Explosion> d = CURRENT.get();
        if (d.isEmpty()) {
            return NORMAL;
        }
        if (ntmFrames.contains(AT_MINE)) {
            return new Fixed(cfg.antiTankMineDamage);
        }
        if (ntmFrames.stream().anyMatch(TNT::contains)) {
            return NORMAL;
        }
        return d.peek().radius() >= cfg.destroyTanksAtExplosionSize ? DESTROY : NORMAL;
    }
}
