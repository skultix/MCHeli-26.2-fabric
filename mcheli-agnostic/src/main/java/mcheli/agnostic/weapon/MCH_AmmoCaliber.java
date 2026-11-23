package mcheli.agnostic.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ammunition of one gun calibre: every gun reloads from the rounds of its own calibre ("20mm Rounds", "105mm
 * Shell"). Calibres up to {@link #MAX_BELT_MM} come as belts of {@link #BELT} rounds, so machine guns and autocannons do
 * not need thousands of items; bigger rounds are one per item. 40 mm grenades and mortar bombs are their own items,
 * apart from a 40 mm cannon round. The cost of an item scales with the mass of what it holds: calibre to the power
 * {@link #MASS_EXPONENT} (close to real cartridge weights: 7.62 mm 25 g, 30 mm 0.7 kg, 125 mm 20 kg) times rounds,
 * set so a 125 mm tank round costs what the original tank shell recipe did.
 *
 * @param mm calibre in millimetres
 * @param kind cannon/gun round, grenade or mortar bomb
 */
public record MCH_AmmoCaliber(float mm, Kind kind) implements Comparable<MCH_AmmoCaliber> {
   public enum Kind { ROUND, GRENADE, MORTAR }

   /** Rounds in one belt. */
   public static final int BELT = 25;
   /** The largest belted calibre: machine guns, autocannons and grenade launchers. */
   public static final float MAX_BELT_MM = 40.0F;
   /** The reference round for costs: a 125 mm tank round costs 5 steel, 4 TATB, a large casing (the tank shell recipe). */
   private static final float REFERENCE_MM = 125.0F;
   /** Round mass grows about as calibre^2.5 (big rounds are longer but thinner-walled, so less than the cube). */
   static final double MASS_EXPONENT = 2.5;

   /**
    * Bundled guns whose names carry no calibre ("M61A1 Vulcan", "GAU-8 Avenger", "Rehinmetall Smoothbore Gun").
    * Content-pack guns without one fall back to {@link #fallback}.
    */
   private static final Map<String, Float> BY_NAME = Map.ofEntries(
      Map.entry("m134", 7.62F), Map.entry("m134_l", 7.62F), Map.entry("m134_l50", 7.62F), Map.entry("m134_r50", 7.62F),
      Map.entry("cb90_m134l", 7.62F), Map.entry("cb90_m134r", 7.62F), Map.entry("mg_coaxial", 7.62F),
      Map.entry("mg3_l", 7.62F), Map.entry("mg3_r", 7.62F), Map.entry("ww2tank_mg", 7.62F),
      Map.entry("ww2tank_coaxmg", 7.62F), Map.entry("yakb-12_7", 12.7F),
      Map.entry("m61a1", 20.0F), Map.entry("m61a1_1", 20.0F), Map.entry("m61a1_2", 20.0F), Map.entry("m61a1_l", 20.0F),
      Map.entry("m61a1_v", 20.0F), Map.entry("m61a1_gpod", 20.0F), Map.entry("m197", 20.0F), Map.entry("gpu-2", 20.0F),
      Map.entry("gau-12u", 25.0F), Map.entry("gau-22a", 25.0F), Map.entry("bk27", 27.0F), Map.entry("rmk30", 30.0F),
      Map.entry("gau-8", 30.0F), Map.entry("a10gau8", 30.0F), Map.entry("m230", 30.0F), Map.entry("m230_30", 30.0F),
      Map.entry("gsh-23-2", 23.0F),
      // the 12.7 cm AA cannon's shell, and the Merkava's canister round for its 120 mm gun
      Map.entry("type3bullet", 127.0F), Map.entry("canistershell", 120.0F),
      Map.entry("rehinmetall_apfsds", 120.0F), Map.entry("rehinmetall_he", 120.0F),
      Map.entry("2a46m-1_apfsds", 125.0F), Map.entry("2a46m-1_heatmp", 125.0F), Map.entry("2a46m-1_he", 125.0F),
      Map.entry("mk19_l", 40.0F), Map.entry("mk19_r", 40.0F), Map.entry("cb90_mk19", 40.0F), Map.entry("gmw", 40.0F),
      Map.entry("gmw_fl", 40.0F));

   /** The calibre of a gun-type weapon ({@link MCH_AmmoClass#isGun}): from its name, the table above, or a fallback. */
   public static MCH_AmmoCaliber of(MCH_WeaponInfo info, MCH_AmmoClass c) {
      Kind kind = c == MCH_AmmoClass.GRENADE ? Kind.GRENADE : c == MCH_AmmoClass.MORTAR ? Kind.MORTAR : Kind.ROUND;
      Float named = BY_NAME.get(info.name.toLowerCase(Locale.ROOT));
      float mm = named != null ? named : MCH_AmmoClass.caliberMm(info.displayName);
      if (Float.isNaN(mm)) {
         mm = MCH_AmmoClass.caliberMm(info.name.replace('_', '.'));
      }
      if (Float.isNaN(mm)) {
         mm = fallback(c);
      }
      return new MCH_AmmoCaliber(Math.round(mm * 100.0F) / 100.0F, kind);
   }

   /** A typical calibre for each kind of gun, for content-pack guns that name none. */
   static float fallback(MCH_AmmoClass c) {
      return switch (c) {
         case BULLET -> 7.62F;
         case AUTOCANNON -> 20.0F;
         case GRENADE -> 40.0F;
         case ARTILLERY -> 155.0F;
         case MORTAR -> 81.0F;
         default -> 120.0F; // tank guns
      };
   }

   /** "7.62mm", "20mm", "105mm". */
   public String label() {
      String n = this.mm == Math.rint(this.mm) ? Integer.toString((int) this.mm)
         : Float.toString(this.mm).replaceAll("0+$", "");
      return n + "mm";
   }

   /** Item id path in the {@code mcheli} namespace: {@code ammo_7_62mm}, {@code ammo_40mm_grenade}, {@code ammo_81mm_mortar}. */
   public String itemPath() {
      String base = "ammo_" + label().replace('.', '_');
      return switch (this.kind) {
         case GRENADE -> base + "_grenade";
         case MORTAR -> base + "_mortar";
         case ROUND -> base;
      };
   }

   public String itemId() {
      return "mcheli:" + itemPath();
   }

   public boolean belted() {
      return this.kind != Kind.MORTAR && this.mm <= MAX_BELT_MM;
   }

   public int roundsPerItem() {
      return belted() ? BELT : 1;
   }

   /** Translation key of the item name, with {@link #label()} as its argument: "%s Rounds", "%s Shell"... */
   public String nameKey() {
      return switch (this.kind) {
         case GRENADE -> "item.mcheli.ammo_grenades";
         case MORTAR -> "item.mcheli.ammo_mortar_bomb";
         case ROUND -> belted() ? "item.mcheli.ammo_rounds" : "item.mcheli.ammo_shell";
      };
   }

   /** Translation key of the tooltip ("A belt of %s %s rounds" / "One %s round"), see {@link #descArgs()}. */
   public String descKey() {
      return belted() ? "item.mcheli.ammo_belt.desc" : "item.mcheli.ammo_single.desc";
   }

   public Object[] descArgs() {
      String what = switch (this.kind) {
         case GRENADE -> label() + " grenade";
         case MORTAR -> label() + " mortar";
         case ROUND -> label();
      };
      return belted() ? new Object[] {BELT, what} : new Object[] {what};
   }

   /** The sprite, by size: rifle and heavy machine-gun belts, small and large cannon belts, grenades, shells, bombs. */
   public String texture() {
      if (this.kind == Kind.GRENADE) {
         return "ammo_grenade";
      }
      if (this.kind == Kind.MORTAR) {
         return "ammo_mortar";
      }
      if (this.mm <= 8.0F) {
         return "ammo_bullet";
      }
      if (this.mm <= 15.0F) {
         return "ammo_hmg";
      }
      if (this.mm <= 27.0F) {
         return "ammo_autocannon_small";
      }
      if (this.mm <= MAX_BELT_MM) {
         return "ammo_autocannon";
      }
      return this.mm < MCH_AmmoClass.MIN_ARTILLERY_MM ? "ammo_tank_shell" : "ammo_artillery_shell";
   }

   /** Relative mass of what one item holds (a 125 mm tank round = 1). */
   double mass() {
      double per = Math.pow(this.mm / REFERENCE_MM, MASS_EXPONENT);
      double scale = switch (this.kind) {
         case GRENADE -> 0.25; // a low-velocity 40 mm grenade (~340 g) is far lighter than a 40 mm cannon round
         case MORTAR -> 0.7;   // a thin-walled bomb with no casing
         case ROUND -> 1.0;
      };
      return per * scale * roundsPerItem();
   }

   /** One ingredient of a recipe: an item id or {@code #tag}, and how many. */
   public record Ingredient(String id, int count) {}

   /** What a recipe makes and takes. */
   public record Cost(int output, List<Ingredient> inputs) {}

   public static final String STEEL = "#c:ingots/steel";
   public static final String STEEL_BLOCK = "#c:storage_blocks/steel";
   public static final String TATB = "hbm:ball_tatb";
   public static final String CORDITE = "hbm:cordite";
   public static final String CASING_SMALL = "hbm:casing_small";
   public static final String CASING_LARGE = "hbm:casing_large";
   public static final String CASING_LARGE_STEEL = "hbm:casing_large_steel";
   /** NTM's ammo press has nine input slots of one stack each. */
   public static final int SLOTS = 9;
   public static final int STACK = 64;

   /**
    * The NTM ammo press recipe, scaled by {@link #mass()}: steel 5, TATB 4 and cordite 2 per tank-round mass (grenades
    * and mortars less propellant, machine-gun bullets no explosive), plus one casing per press for cased rounds. Light
    * items come several to a press, so the smallest ingredient is still a whole item.
    */
   public Cost pressCost() {
      double m = mass();
      boolean explosive = this.kind != Kind.ROUND || this.mm >= 20.0F;
      double steel = 5.0 * m;
      double tatb = explosive ? 4.0 * m : 0.0;
      double cordite = switch (this.kind) {
         case GRENADE -> 0.5 * m;
         case MORTAR -> 1.0 * m;
         case ROUND -> 2.0 * m;
      };
      int out = 1;
      while (out < STACK && steel * out < 1.0) {
         out *= 2;
      }
      List<Ingredient> in = new ArrayList<>();
      int steelN = Math.max(1, (int) Math.round(steel * out));
      // Past what nine stacks allow, steel goes in as blocks.
      if (steelN > 3 * STACK) {
         split(in, STEEL_BLOCK, (steelN + 8) / 9);
      } else {
         split(in, STEEL, steelN);
      }
      if (tatb > 0.0) {
         split(in, TATB, Math.max(1, (int) Math.round(tatb * out)));
      }
      split(in, CORDITE, Math.max(1, (int) Math.round(cordite * out)));
      String casing = casing();
      if (casing != null) {
         in.add(new Ingredient(casing, 1));
      }
      if (in.size() > SLOTS) {
         throw new IllegalStateException(label() + " needs " + in.size() + " press slots");
      }
      return new Cost(out, in);
   }

   /**
    * The crafting-table recipe used without NTM: the press recipe in vanilla materials (steel → iron, nine to a block
    * past six; TATB → TNT, four to one; cordite → gunpowder; a casing → a copper ingot), one item per grid slot, the
    * largest count trimmed until it fits the 3×3 grid.
    */
   public Cost craftCost() {
      Cost press = pressCost();
      int steel = 0;
      int tatb = 0;
      int cordite = 0;
      boolean cased = false;
      for (Ingredient i : press.inputs()) {
         switch (i.id()) {
            case STEEL -> steel += i.count();
            case STEEL_BLOCK -> steel += 9 * i.count();
            case TATB -> tatb += i.count();
            case CORDITE -> cordite += i.count();
            default -> cased = true;
         }
      }
      boolean blocks = steel > 6;
      int[] n = {blocks ? (steel + 8) / 9 : steel, tatb == 0 ? 0 : (tatb + 3) / 4, cordite, cased ? 1 : 0};
      String[] id = {blocks ? "minecraft:iron_block" : "minecraft:iron_ingot", "minecraft:tnt", "minecraft:gunpowder",
         "minecraft:copper_ingot"};
      while (n[0] + n[1] + n[2] + n[3] > SLOTS) {
         int big = 0;
         for (int k = 1; k < 3; k++) {
            if (n[k] > n[big]) {
               big = k;
            }
         }
         n[big]--;
      }
      List<Ingredient> in = new ArrayList<>();
      for (int k = 0; k < 4; k++) {
         if (n[k] > 0) {
            in.add(new Ingredient(id[k], n[k]));
         }
      }
      return new Cost(press.output(), in);
   }

   /** The casing a press makes these rounds in, or null (mortar bombs have none). */
   String casing() {
      if (this.kind == Kind.MORTAR) {
         return null;
      }
      if (this.kind == Kind.GRENADE || this.mm <= 15.0F) {
         return CASING_SMALL;
      }
      return this.mm < MCH_AmmoClass.MIN_ARTILLERY_MM ? CASING_LARGE : CASING_LARGE_STEEL;
   }

   private static void split(List<Ingredient> in, String id, int count) {
      while (count > 0) {
         int n = Math.min(STACK, count);
         in.add(new Ingredient(id, n));
         count -= n;
      }
   }

   @Override
   public int compareTo(MCH_AmmoCaliber o) {
      int k = this.kind.compareTo(o.kind);
      return k != 0 ? k : Float.compare(this.mm, o.mm);
   }
}
