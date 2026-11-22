package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mcheli.agnostic.spi.Role;

/**
 * Server-side damage tuning for MCHeli's vehicle weapons. It plays the part of the original mod's
 * {@code DamageVs*} settings in {@code mcheli.cfg} and extends it with multipliers per weapon type and per
 * weapon, and with overrides of a weapon's config values.
 *
 * <p>The fields are public and plain, so the platform layer can read and write this class directly as JSON
 * (Gson). A null in a nested entry means "not set". See {@code docs/configuring-damage.md}.
 *
 * <p>Damage dealt by a weapon:
 * <pre>
 *   direct hit = power     × damageFactor(target) × directDamage    × type.directDamage    × weapon.directDamage    × vsTarget(target)
 *   explosion  = blast(t)  × damageFactor(target) × explosionDamage × type.explosionDamage × weapon.explosionDamage × vsTarget(target)
 * </pre>
 * {@code power}, {@code explosion*} and {@code damageFactor} are the weapon's config values after {@link #weapons}
 * overrides, which {@link #apply} writes into the loaded {@link MCH_WeaponInfo}s.
 */
public class MCH_DamageSettings {
   /** Target categories accepted by {@link #vsTarget} and {@link WeaponOverride#damageFactor}. */
   public static final String[] TARGETS = {"player", "living", "other", "helicopter", "plane", "tank", "vehicle"};
   /** The weapon {@code Type}s of the bundled configs, pre-listed in a fresh {@link #weaponTypes}. */
   public static final String[] TYPES = {"MachineGun1", "MachineGun2", "Rocket", "MkRocket", "ASMissile", "AAMissile",
      "ATMissile", "TVMissile", "Bomb", "Torpedo", "CAS", "Dispenser"};

   public String _readme = "MCHeli weapon damage. Reference: docs/configuring-damage.md in the MCHeli Fabric repo. "
      + "Every weapon's unmodified values are listed in mcheli-damage-defaults.json.";
   /** Multiplier on every direct hit by an MCHeli round. */
   public float directDamage = 1.0F;
   /** Multiplier on the entity damage of every MCHeli weapon explosion (not its size or block damage). */
   public float explosionDamage = 1.0F;
   /** Multipliers per weapon {@code Type} (case-insensitive). */
   public Map<String, Multipliers> weaponTypes = new LinkedHashMap<>();
   /** Multiplier per target category, applied to direct hits and explosions alike (original: {@code DamageVs*}). */
   public Map<String, Float> vsTarget = new LinkedHashMap<>();
   /** Per-weapon overrides, keyed by the weapon's config name (its file name, e.g. {@code m230}). */
   public Map<String, WeaponOverride> weapons = new LinkedHashMap<>();
   /** Rules for TaCZ guns hitting MCHeli vehicles (only used when TaCZ is installed). */
   public Tacz tacz = new Tacz();
   /** What happens to the players riding a vehicle. */
   public Crew crew = new Crew();

   public static class Multipliers {
      public Float directDamage;
      public Float explosionDamage;

      public Multipliers() {}

      public Multipliers(Float directDamage, Float explosionDamage) {
         this.directDamage = directDamage;
         this.explosionDamage = explosionDamage;
      }
   }

   public static class WeaponOverride extends Multipliers {
      public Integer power;
      public Integer explosion;
      public Integer explosionBlock;
      public Integer explosionInWater;
      /** Target category -> factor; replaces the config's {@code DamageFactor} for the listed categories only. */
      public Map<String, Float> damageFactor;
   }

   public static class Tacz {
      /** Plain (non-explosive) TaCZ bullets against an unarmoured vehicle. */
      public float smallArmsFactor = 0.5F;
      /** Plain TaCZ bullets against an armoured vehicle. 0 = immune. */
      public float smallArmsVsArmoredFactor = 0.0F;
      /** A vehicle counts as armoured when its config {@code ArmorMinDamage} is at least this. */
      public float armoredMinDamage = 5.0F;
   }

   public static class Crew {
      /** Riders take no damage while their vehicle is intact (/kill and the void still apply). */
      public boolean protectedWhileMounted = true;
      /** Riders die when their vehicle is destroyed. */
      public boolean diesWithVehicle = true;
   }

   /** A settings object with every list filled in at its neutral value, which is what a fresh config file holds. */
   public static MCH_DamageSettings defaults() {
      MCH_DamageSettings s = new MCH_DamageSettings();
      for (String t : TYPES) {
         s.weaponTypes.put(t, new Multipliers(1.0F, 1.0F));
      }
      for (String t : TARGETS) {
         s.vsTarget.put(t, 1.0F);
      }
      return s;
   }

   /** Problems worth logging (unknown keys, negative values); also clamps what it can. */
   public List<String> validate() {
      List<String> w = new ArrayList<>();
      if (this.weaponTypes == null) this.weaponTypes = new LinkedHashMap<>();
      if (this.vsTarget == null) this.vsTarget = new LinkedHashMap<>();
      if (this.weapons == null) this.weapons = new LinkedHashMap<>();
      if (this.tacz == null) this.tacz = new Tacz();
      if (this.crew == null) this.crew = new Crew();
      this.directDamage = nonNegative(this.directDamage, "directDamage", w);
      this.explosionDamage = nonNegative(this.explosionDamage, "explosionDamage", w);
      for (String k : this.vsTarget.keySet()) {
         if (role(k) == null) {
            w.add("vsTarget: unknown target '" + k + "' (known: " + String.join(", ", TARGETS) + ")");
         }
      }
      for (Map.Entry<String, WeaponOverride> e : this.weapons.entrySet()) {
         WeaponOverride o = e.getValue();
         if (o == null) {
            continue;
         }
         if (MCH_WeaponInfoManager.get(e.getKey().toLowerCase(Locale.ROOT)) == null && !MCH_WeaponInfoManager.getInstance().getMap().isEmpty()) {
            w.add("weapons: no weapon named '" + e.getKey() + "' is loaded");
         }
         if (o.damageFactor != null) {
            for (String k : o.damageFactor.keySet()) {
               if (role(k) == null) {
                  w.add("weapons." + e.getKey() + ".damageFactor: unknown target '" + k + "'");
               }
            }
         }
      }
      this.tacz.smallArmsFactor = nonNegative(this.tacz.smallArmsFactor, "tacz.smallArmsFactor", w);
      this.tacz.smallArmsVsArmoredFactor = nonNegative(this.tacz.smallArmsVsArmoredFactor, "tacz.smallArmsVsArmoredFactor", w);
      return w;
   }

   private static float nonNegative(float v, String name, List<String> w) {
      if (v < 0.0F || Float.isNaN(v)) {
         w.add(name + " = " + v + " is negative; using 0");
         return 0.0F;
      }
      return v;
   }

   /** Multiplier on a direct hit by {@code weapon} (may be null for an unknown round) against {@code target}. */
   public float directMultiplier(MCH_WeaponInfo weapon, Role target) {
      float m = this.directDamage * vs(target);
      if (weapon != null) {
         Multipliers t = type(weapon.type);
         WeaponOverride o = override(weapon.name);
         m *= value(t != null ? t.directDamage : null) * value(o != null ? o.directDamage : null);
      }
      return m;
   }

   /** Multiplier on the entity damage of an explosion from {@code weapon} (may be null) against {@code target}. */
   public float explosionMultiplier(MCH_WeaponInfo weapon, Role target) {
      float m = this.explosionDamage * vs(target);
      if (weapon != null) {
         Multipliers t = type(weapon.type);
         WeaponOverride o = override(weapon.name);
         m *= value(t != null ? t.explosionDamage : null) * value(o != null ? o.explosionDamage : null);
      }
      return m;
   }

   private float vs(Role target) {
      if (target == null) {
         return 1.0F;
      }
      for (Map.Entry<String, Float> e : this.vsTarget.entrySet()) {
         if (role(e.getKey()) == target && e.getValue() != null) {
            return Math.max(0.0F, e.getValue());
         }
      }
      return 1.0F;
   }

   private Multipliers type(String type) {
      if (type == null) {
         return null;
      }
      for (Map.Entry<String, Multipliers> e : this.weaponTypes.entrySet()) {
         if (e.getKey().equalsIgnoreCase(type)) {
            return e.getValue();
         }
      }
      return null;
   }

   private WeaponOverride override(String name) {
      if (name == null) {
         return null;
      }
      for (Map.Entry<String, WeaponOverride> e : this.weapons.entrySet()) {
         if (e.getKey().equalsIgnoreCase(name)) {
            return e.getValue();
         }
      }
      return null;
   }

   private static float value(Float f) {
      return f != null ? Math.max(0.0F, f) : 1.0F;
   }

   /** Target category name (as in the weapon configs' {@code DamageFactor}) -> role; null if unknown. */
   public static Role role(String name) {
      switch (name.toLowerCase(Locale.ROOT)) {
         case "player": return Role.PLAYER;
         case "living": return Role.LIVING;
         case "other": return Role.OTHER;
         case "heli":
         case "helicopter": return Role.HELICOPTER;
         case "plane": return Role.PLANE;
         case "tank": return Role.TANK;
         case "vehicle": return Role.VEHICLE;
         default: return null;
      }
   }

   public static String targetName(Role role) {
      return role.name().toLowerCase(Locale.ROOT);
   }

   // ---- overrides of the loaded weapon configs ----

   /** A weapon's damage values as its config file defined them, before any override. */
   public static final class Original {
      public final int power;
      public final int explosion;
      public final int explosionBlock;
      public final int explosionInWater;
      public final MCH_DamageFactor damageFactor;

      Original(MCH_WeaponInfo wi) {
         this.power = wi.power;
         this.explosion = wi.explosion;
         this.explosionBlock = wi.explosionBlock;
         this.explosionInWater = wi.explosionInWater;
         this.damageFactor = wi.damageFactor != null ? wi.damageFactor.copy() : null;
      }

      void restore(MCH_WeaponInfo wi) {
         wi.power = this.power;
         wi.explosion = this.explosion;
         wi.explosionBlock = this.explosionBlock;
         wi.explosionInWater = this.explosionInWater;
         wi.damageFactor = this.damageFactor != null ? this.damageFactor.copy() : null;
      }
   }

   private static final Map<MCH_WeaponInfo, Original> ORIGINALS = new IdentityHashMap<>();

   /** The config-file values of {@code wi}, recorded the first time {@link #apply} saw it. */
   public static synchronized Original original(MCH_WeaponInfo wi) {
      return ORIGINALS.computeIfAbsent(wi, Original::new);
   }

   /**
    * Writes the {@link #weapons} overrides into the loaded weapon infos. Each weapon is first restored to its
    * config-file values, so applying a changed settings object (a reload) never stacks on the previous one.
    */
   public void apply(Collection<MCH_WeaponInfo> infos) {
      for (MCH_WeaponInfo wi : infos) {
         Original orig = original(wi);
         orig.restore(wi);
         WeaponOverride o = override(wi.name);
         if (o == null) {
            continue;
         }
         if (o.power != null) {
            wi.power = Math.max(0, o.power);
         }
         if (o.explosion != null) {
            wi.explosion = clamp(o.explosion, 0, 50);
            if (o.explosionBlock == null && orig.explosionBlock == orig.explosion) {
               wi.explosionBlock = wi.explosion; // the config left ExplosionBlock to follow Explosion
            }
         }
         if (o.explosionBlock != null) {
            wi.explosionBlock = clamp(o.explosionBlock, 0, 50);
         }
         if (o.explosionInWater != null) {
            wi.explosionInWater = clamp(o.explosionInWater, 0, 50);
         }
         if (o.damageFactor != null && !o.damageFactor.isEmpty()) {
            MCH_DamageFactor f = wi.damageFactor != null ? wi.damageFactor : new MCH_DamageFactor();
            for (Map.Entry<String, Float> e : o.damageFactor.entrySet()) {
               Role r = role(e.getKey());
               if (r != null && e.getValue() != null) {
                  f.add(r, Math.max(0.0F, e.getValue()));
               }
            }
            wi.damageFactor = f;
         }
      }
   }

   private static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }
}
