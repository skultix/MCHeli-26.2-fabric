package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The basic ammunition a weapon is reloaded with. The original mod reloaded every weapon from its own list of raw
 * materials ({@code Item = 2, iron_ingot}); this port reloads from four crafted ammo items instead, one item per
 * supply:
 * <ul>
 *   <li>{@link #BULLET}: guns up to 12.7 mm;</li>
 *   <li>{@link #SHELL}: guns larger than 12.7 mm, and gun-fired shells, mortars and grenades;</li>
 *   <li>{@link #MISSILE}: guided ordnance (AA, AT, TV and air-to-surface missiles, guided bombs);</li>
 *   <li>{@link #BOMB}: unguided, non-gun ordnance (bombs, rockets, torpedoes, which run straight in MCHeli).</li>
 * </ul>
 * Weapons that fire no ammunition (dispensers, smoke, targeting pods, dummies) are {@link #NONE} and keep their
 * config's own items.
 */
public enum MCH_AmmoClass {
   BULLET("ammo_bullet"),
   SHELL("ammo_tank_shell"),
   MISSILE("ammo_missile"),
   BOMB("ammo_bomb"),
   NONE(null);

   /** The largest bullet calibre, in millimetres. */
   public static final float MAX_BULLET_MM = 12.7F;
   /** A gun with no calibre in its name and at most this power is a rifle-calibre machine gun. */
   public static final int MAX_BULLET_POWER = 10;

   private static final Pattern CALIBER = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mm|cm)", Pattern.CASE_INSENSITIVE);

   /** Item id path (in the {@code mcheli} namespace), or null for {@link #NONE}. */
   public final String itemPath;

   MCH_AmmoClass(String itemPath) {
      this.itemPath = itemPath;
   }

   public String itemId() {
      return this.itemPath == null ? null : "mcheli:" + this.itemPath;
   }

   public static MCH_AmmoClass classify(MCH_WeaponInfo info) {
      String type = info.type == null ? "" : info.type.toLowerCase(Locale.ROOT);
      String label = ((info.displayName == null ? "" : info.displayName) + " " + info.name).toLowerCase(Locale.ROOT);
      switch (type) {
         case "machinegun1", "machinegun2", "cas" -> {
            float mm = caliberMm(info.displayName);
            if (Float.isNaN(mm)) {
               mm = caliberMm(info.name.replace('_', '.'));
            }
            if (!Float.isNaN(mm)) {
               return mm <= MAX_BULLET_MM ? BULLET : SHELL;
            }
            return info.power <= MAX_BULLET_POWER ? BULLET : SHELL;
         }
         case "rocket", "mkrocket" -> {
            return label.contains("shell") || label.contains("mortar") || label.contains("grenade")
               || label.contains("gmw") ? SHELL : BOMB;
         }
         case "bomb", "torpedo" -> {
            return BOMB;
         }
         case "aamissile", "atmissile", "tvmissile", "asmissile" -> {
            return MISSILE;
         }
         default -> {
            return NONE;
         }
      }
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
    * Replaces each weapon's reload cost with one of its basic ammo item. Weapons classed {@link #NONE} keep theirs.
    * Returns how many weapons were changed.
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
         n++;
      }
      return n;
   }
}
