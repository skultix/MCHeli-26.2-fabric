package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ammunition a weapon is reloaded with. The original mod reloaded every weapon from its own list of raw materials
 * ({@code Item = 2, iron_ingot}); this port reloads from crafted ammo items, each the ammunition that weapon really
 * fires, paid per round. An item holds {@link #roundsPerItem} rounds, which its tooltip states: the belt-fed calibres
 * come as belts, everything else is one round per item.
 * <ul>
 *   <li>{@link #BULLET}: guns up to 12.7 mm (a belt of 25);</li>
 *   <li>{@link #AUTOCANNON}: 20&ndash;40 mm cannons (a belt of 20);</li>
 *   <li>{@link #GRENADE}: 40 mm grenade launchers (Mk19, GMW, M75);</li>
 *   <li>{@link #SHELL}: tank guns;</li>
 *   <li>{@link #ARTILLERY}: howitzers, naval guns and their shells;</li>
 *   <li>{@link #MORTAR}: mortars;</li>
 *   <li>{@link #MISSILE}: guided ordnance (AA, AT, TV and air-to-surface missiles, guided bombs);</li>
 *   <li>{@link #ROCKET}: unguided rockets;</li>
 *   <li>{@link #BOMB}: unguided bombs;</li>
 *   <li>{@link #TORPEDO}: torpedoes.</li>
 * </ul>
 * Weapons that fire no ammunition (dispensers, smoke, targeting pods, dummies) are {@link #NONE} and keep their
 * config's own items.
 */
public enum MCH_AmmoClass {
   BULLET("ammo_bullet", 25),
   AUTOCANNON("ammo_autocannon", 20),
   GRENADE("ammo_grenade", 1),
   SHELL("ammo_tank_shell", 1),
   ARTILLERY("ammo_artillery_shell", 1),
   MORTAR("ammo_mortar", 1),
   MISSILE("ammo_missile", 1),
   ROCKET("ammo_rocket", 1),
   BOMB("ammo_bomb", 1),
   TORPEDO("ammo_torpedo", 1),
   NONE(null, 0);

   /** The largest bullet calibre, in millimetres. */
   public static final float MAX_BULLET_MM = 12.7F;
   /** The largest autocannon calibre, in millimetres. */
   public static final float MAX_AUTOCANNON_MM = 40.0F;
   /** Guns this big or bigger are artillery (naval guns, heavy howitzers), not tank guns. */
   public static final float MIN_ARTILLERY_MM = 127.0F;
   /** A gun with no calibre in its name and at most this power is a rifle-calibre machine gun. */
   public static final int MAX_BULLET_POWER = 10;
   /** A cannon with no calibre in its name, supplied at least this many rounds at once, is an autocannon. */
   public static final int AUTOCANNON_SUPPLY = 50;

   /** Bundled weapons the rules cannot tell apart by name: the OTO Melara 76 mm is a naval gun, and the A-10 strike
    *  the attack helicopters call in fires the GAU-8. */
   private static final Map<String, MCH_AmmoClass> BY_NAME = Map.of("76mmgun", ARTILLERY, "a10gau8", AUTOCANNON);

   private static final Pattern CALIBER = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mm|cm)", Pattern.CASE_INSENSITIVE);

   /** Item id path (in the {@code mcheli} namespace), or null for {@link #NONE}. */
   public final String itemPath;
   /** Rounds one item reloads. */
   public final int roundsPerItem;

   MCH_AmmoClass(String itemPath, int roundsPerItem) {
      this.itemPath = itemPath;
      this.roundsPerItem = roundsPerItem;
   }

   public String itemId() {
      return this.itemPath == null ? null : "mcheli:" + this.itemPath;
   }

   public static MCH_AmmoClass classify(MCH_WeaponInfo info) {
      MCH_AmmoClass named = BY_NAME.get(info.name.toLowerCase(Locale.ROOT));
      if (named != null) {
         return named;
      }
      String type = info.type == null ? "" : info.type.toLowerCase(Locale.ROOT);
      String label = ((info.displayName == null ? "" : info.displayName) + " " + info.name).toLowerCase(Locale.ROOT);
      boolean grenade = label.contains("grenade") || label.contains("gmw") || label.contains("mk19");
      switch (type) {
         case "machinegun1", "machinegun2", "cas" -> {
            if (grenade) {
               return GRENADE;
            }
            if (label.contains("mortar")) {
               return MORTAR;
            }
            if (label.contains("howitzer")) {
               return ARTILLERY;
            }
            float mm = caliberMm(info.displayName);
            if (Float.isNaN(mm)) {
               mm = caliberMm(info.name.replace('_', '.'));
            }
            if (Float.isNaN(mm)) {
               if (info.power <= MAX_BULLET_POWER) {
                  return BULLET;
               }
               return info.suppliedNum >= AUTOCANNON_SUPPLY ? AUTOCANNON : SHELL;
            }
            if (mm <= MAX_BULLET_MM) {
               return BULLET;
            }
            if (mm <= MAX_AUTOCANNON_MM) {
               return AUTOCANNON;
            }
            return mm >= MIN_ARTILLERY_MM ? ARTILLERY : SHELL;
         }
         case "rocket", "mkrocket" -> {
            if (grenade) {
               return GRENADE;
            }
            if (label.contains("mortar")) {
               return MORTAR;
            }
            return label.contains("shell") ? ARTILLERY : ROCKET; // gun-fired "Rocket"-type rounds: naval AA, canister
         }
         case "bomb" -> {
            return BOMB;
         }
         case "torpedo" -> {
            return TORPEDO;
         }
         case "aamissile", "atmissile", "tvmissile", "asmissile" -> {
            return MISSILE;
         }
         default -> {
            return NONE;
         }
      }
   }

   /** Items a reload of {@code missingRounds} takes at {@code roundsPerItem} rounds each (a last belt may be part-used). */
   public static int itemsFor(int missingRounds, int roundsPerItem) {
      return missingRounds <= 0 ? 0 : (missingRounds + roundsPerItem - 1) / roundsPerItem;
   }

   /** The calibre written in a name ("M2 12.7mm", "15.5cm Type91", "105mmgun"), in mm, or NaN. */
   static float caliberMm(String s) {
      if (s == null) {
         return Float.NaN;
      }
      Matcher m = CALIBER.matcher(s);
      if (!m.find()) {
         return Float.NaN;
      }
      float v = Float.parseFloat(m.group(1));
      return m.group(2).equalsIgnoreCase("cm") ? v * 10.0F : v;
   }

   /**
    * Replaces each weapon's reload cost with its ammo item, paid per round ({@link #roundsPerItem}). Weapons classed
    * {@link #NONE} keep theirs. Returns how many weapons were changed.
    */
   public static int apply(Collection<MCH_WeaponInfo> weapons) {
      int n = 0;
      for (MCH_WeaponInfo wi : weapons) {
         MCH_AmmoClass c = classify(wi);
         if (c == NONE) {
            continue;
         }
         List<MCH_WeaponInfo.RoundItem> cost = new ArrayList<>();
         cost.add(new MCH_WeaponInfo.RoundItem(1, c.itemId(), 0));
         wi.roundItems = cost;
         wi.roundsPerAmmoItem = c.roundsPerItem;
         n++;
      }
      return n;
   }
}
