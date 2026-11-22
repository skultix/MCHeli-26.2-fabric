package mcheli.agnostic.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vehicle recipes for the NTM (HBM Nuclear Tech) assembly machine. Each bundled vehicle has a {@link Profile} (what
 * kind of machine it is and how modern) and a size; the profile lists its materials at size 1, and the bulk
 * materials scale with the size. Vehicles from content packs get a profile and size from their category, hit points
 * and armament.
 *
 * <p>Everything here comes from NTM's early-to-midgame tiers: steel, industrial copper, aluminium, titanium,
 * high-speed steel (crucible alloy), welded (arc welder) and cast (foundry) plates, Aluminium Shells, motors,
 * integrated circuits and TATB. The assembly machine has 12 input slots and takes at most a stack of an item per
 * slot, which {@link #MAX_INPUTS} and {@link #MAX_STACK} enforce.
 */
public final class MCH_VehicleAssembly {
   public static final int MAX_INPUTS = 12;
   public static final int MAX_STACK = 64;

   // NTM ingredients (tags where NTM's own recipes use them, so other mods' metals count too).
   public static final String STEEL = "#c:ingots/steel";
   public static final String STEEL_PLATE = "#c:plates/steel";
   public static final String STEEL_WELDED = "hbm:plate_welded_steel";
   public static final String STEEL_CAST = "hbm:plate_cast_steel";
   public static final String COPPER = "#c:ingots/copper";
   public static final String HSS = "#c:ingots/dura_steel";
   public static final String ALU = "#c:ingots/aluminum";
   public static final String ALU_PLATE = "#c:plates/aluminum";
   public static final String ALU_WELDED = "hbm:plate_welded_aluminum";
   public static final String ALU_CAST = "hbm:plate_cast_aluminum";
   public static final String ALU_SHELL = "hbm:shell_aluminum";         // Aluminium Shell (anvil, 4 plates)
   public static final String TITANIUM = "#c:ingots/titanium";
   public static final String TITANIUM_PLATE = "#c:plates/titanium";
   public static final String TITANIUM_WELDED = "hbm:plate_welded_titanium";
   public static final String GEAR_TRAIN = "hbm:weapon_mod_special_slowdown";
   public static final String MOTOR = "hbm:motor";
   public static final String CIRCUIT = "hbm:circuit_basic";        // Integrated Circuit Board
   public static final String ANALOG = "hbm:circuit_analog";        // Analog Circuit Board (pre-IC era)
   public static final String PCB = "hbm:circuit_pcb";
   public static final String CRT = "hbm:crt_display";             // radar / sight display
   public static final String TATB = "hbm:ball_tatb";
   public static final String BARREL_HEAVY = "hbm:part_barrel_heavy_steel";
   public static final String BARREL_HEAVY_HSS = "hbm:part_barrel_heavy_dura_steel";
   public static final String BARREL_LIGHT = "hbm:part_barrel_light_steel";
   public static final String MECHANISM = "hbm:part_mechanism_gun_metal";
   public static final String RUBBER = "#c:ingots/rubber";
   public static final String POLYMER = "hbm:plate_polymer";
   public static final String PIPE = "hbm:pipe_steel";
   public static final String FLUID_TANK = "hbm:tank_steel";
   public static final String GLASS = "minecraft:glass_pane";

   public record Input(String ingredient, int count) {}

   public record Recipe(List<Input> inputs, int durationTicks, int power) {
      public int count(String ingredient) {
         return this.inputs.stream().filter(i -> i.ingredient.equals(ingredient)).mapToInt(Input::count).sum();
      }
   }

   /** What kind of machine a vehicle is. {@code modern} decides integrated vs analog circuits where both fit. */
   public enum Profile {
      TANK_WW2, TANK_MODERN, IFV, WHEELED_AFV, LIGHT_ARMORED, CAR, TRUCK,
      HELI_LIGHT, HELI_UTILITY, HELI_ATTACK, HELI_HEAVY, HELI_DRONE,
      PROP, PROP_HEAVY, JET, JET_STEALTH, JET_HEAVY, PLANE_DRONE, BOAT,
      GUN, CIWS, SAM, TORPEDO, AMMO_BOX
   }

   /** A bundled vehicle: its profile, size, era and any per-vehicle changes ({@code 0} removes an input). */
   private record Entry(Profile profile, float size, boolean modern, Map<String, Integer> changes) {}

   private static final Map<String, Entry> BUNDLED = new LinkedHashMap<>();

   private static void put(String name, Profile p, float size, boolean modern, Object... changes) {
      Map<String, Integer> c = new LinkedHashMap<>();
      for (int i = 0; i < changes.length; i += 2) {
         c.put((String) changes[i], (Integer) changes[i + 1]);
      }
      BUNDLED.put(name, new Entry(p, size, modern, c));
   }

   static {
      // ---- tanks and ground vehicles ----
      put("kv-2", Profile.TANK_WW2, 1.15F, false);                       // 52 t, 152 mm howitzer turret
      put("m26", Profile.TANK_WW2, 1.0F, false);
      put("m1a2", Profile.TANK_MODERN, 1.1F, true, TATB, 8);             // composite armour, little ERA
      put("merkava_mk4", Profile.TANK_MODERN, 1.1F, true, TATB, 8);      // modular armour
      put("t-90", Profile.TANK_MODERN, 0.95F, true, TATB, 12);           // Kontakt-5 / Relikt ERA
      put("t84", Profile.TANK_MODERN, 1.0F, true, TATB, 14);             // Duplet ERA
      put("kurganets25", Profile.IFV, 1.0F, true);
      put("centauro", Profile.WHEELED_AFV, 1.0F, true);
      put("m1129", Profile.WHEELED_AFV, 0.9F, true);
      put("mxtmv", Profile.LIGHT_ARMORED, 1.0F, true);
      put("growler", Profile.LIGHT_ARMORED, 0.7F, true);
      put("bnr32", Profile.CAR, 1.0F, true);
      // ---- helicopters ----
      put("fl282", Profile.HELI_LIGHT, 0.6F, false);
      put("bell47g", Profile.HELI_LIGHT, 0.8F, false);
      put("bell47gf", Profile.HELI_LIGHT, 0.8F, false);
      put("robinson_r44", Profile.HELI_LIGHT, 0.9F, true);
      put("robinson_r44f", Profile.HELI_LIGHT, 0.9F, true);
      put("bell206l", Profile.HELI_LIGHT, 1.0F, true);
      put("mh-6", Profile.HELI_LIGHT, 1.0F, true);
      put("ah-6x", Profile.HELI_ATTACK, 0.55F, true);
      put("ah-6", Profile.HELI_ATTACK, 0.6F, true);
      put("bell207", Profile.HELI_ATTACK, 0.7F, false);
      put("oh-1", Profile.HELI_ATTACK, 0.8F, true);
      put("penetrator", Profile.HELI_ATTACK, 0.85F, true);
      put("ec665", Profile.HELI_ATTACK, 0.95F, true);
      put("ah-1z", Profile.HELI_ATTACK, 1.0F, true);
      put("ka50n", Profile.HELI_ATTACK, 1.0F, true);
      put("ka52", Profile.HELI_ATTACK, 1.05F, true);
      put("mh-60l_dap", Profile.HELI_ATTACK, 1.0F, true);
      put("ah-64", Profile.HELI_ATTACK, 1.1F, true, CIRCUIT, 5, TITANIUM_PLATE, 24); // Longbow radar, armoured tub
      put("mi28", Profile.HELI_ATTACK, 1.15F, true);
      put("mi-24", Profile.HELI_ATTACK, 1.3F, true);
      put("uh-1c", Profile.HELI_UTILITY, 0.8F, false);
      put("mh-60g", Profile.HELI_UTILITY, 1.0F, true);
      put("sh-60", Profile.HELI_UTILITY, 1.0F, true, CIRCUIT, 3);         // dipping sonar / search radar
      put("nh90", Profile.HELI_UTILITY, 1.1F, true);
      put("ka-27", Profile.HELI_UTILITY, 1.05F, true);
      put("ka-29", Profile.HELI_UTILITY, 1.05F, true);
      put("sh-3", Profile.HELI_UTILITY, 1.1F, false);
      put("mi-24ps", Profile.HELI_UTILITY, 1.3F, true);
      put("ch47", Profile.HELI_HEAVY, 1.2F, true);
      put("mh-53e", Profile.HELI_HEAVY, 1.3F, true);
      put("mq-8b", Profile.HELI_DRONE, 1.3F, true);
      put("rc-goblin", Profile.HELI_DRONE, 0.3F, true);
      put("rc-goblin-bomb", Profile.HELI_DRONE, 0.35F, true, TATB, 2);
      // ---- propeller aircraft ----
      put("macchi_m33", Profile.PROP, 0.7F, false);
      put("macchi_mc72", Profile.PROP, 0.8F, false);
      put("f1m", Profile.PROP, 0.8F, false);
      put("a6m2", Profile.PROP, 0.9F, false);
      put("n1k1", Profile.PROP, 0.9F, false);
      put("fi167", Profile.PROP, 0.9F, false);
      put("p-51d", Profile.PROP, 1.0F, false);
      put("an2", Profile.PROP, 1.0F, false);
      put("cessna172", Profile.PROP, 0.7F, true);
      put("pzl-m18", Profile.PROP, 0.9F, true);
      put("emb314", Profile.PROP, 1.0F, true);
      put("p180", Profile.PROP, 1.0F, true);
      put("h8k", Profile.PROP_HEAVY, 1.1F, false);
      put("b29", Profile.PROP_HEAVY, 1.3F, false);
      put("us-2", Profile.PROP_HEAVY, 1.1F, true);
      put("mv-22", Profile.PROP_HEAVY, 1.2F, true, GEAR_TRAIN, 2, TITANIUM_PLATE, 16, GLASS, 0, BARREL_LIGHT, 0,
          MECHANISM, 0);                                                    // tilting rotor gearboxes
      put("ac-130", Profile.PROP_HEAVY, 1.4F, true, CRT, 2, GLASS, 0);    // sensor suite; 105 mm and 40 mm guns
      // ---- jets ----
      put("t-4", Profile.JET, 0.6F, true);
      put("f4a", Profile.JET, 0.9F, true);
      put("mig29", Profile.JET, 0.9F, true);
      put("harrier", Profile.JET, 0.85F, true, GEAR_TRAIN, 1);           // vectored-thrust nozzles
      put("harrier_en", Profile.JET, 0.85F, true, GEAR_TRAIN, 1);
      put("tornado-ids", Profile.JET, 0.95F, true);
      put("tornado-gr4", Profile.JET, 1.0F, true);
      put("fa18f", Profile.JET, 1.0F, true);
      put("f-15e", Profile.JET, 1.05F, true);
      put("f-15s_mtd", Profile.JET, 1.05F, true);
      put("f14d", Profile.JET, 1.1F, true, GEAR_TRAIN, 1);               // swing wings
      put("su-33", Profile.JET, 1.1F, true);
      put("su37", Profile.JET, 1.1F, true);
      put("su-47", Profile.JET, 1.15F, true);
      put("a-10", Profile.JET, 1.1F, true, TITANIUM_PLATE, 40, BARREL_LIGHT, 0, BARREL_HEAVY_HSS, 2); // titanium tub, GAU-8
      put("x-47b", Profile.JET_STEALTH, 0.6F, true);
      put("f-35a", Profile.JET_STEALTH, 1.0F, true);
      put("f-35c", Profile.JET_STEALTH, 1.0F, true);
      put("f-35b", Profile.JET_STEALTH, 1.05F, true, GEAR_TRAIN, 1, PCB, 0, CIRCUIT, 8); // lift fan
      put("f22a", Profile.JET_STEALTH, 1.1F, true);
      put("b-2a", Profile.JET_STEALTH, 2.0F, true);
      put("md90", Profile.JET_HEAVY, 1.1F, true);
      put("e767", Profile.JET_HEAVY, 1.3F, true, CRT, 4);                // AWACS radar
      put("c5", Profile.JET_HEAVY, 1.6F, true);
      put("skylark", Profile.PLANE_DRONE, 0.4F, true);
      put("mqm170", Profile.PLANE_DRONE, 0.5F, true);
      put("bqm_74e", Profile.PLANE_DRONE, 0.6F, true);
      put("mq-9", Profile.PLANE_DRONE, 1.4F, true, CRT, 1);
      // ---- boats and the truck (listed with the planes in MCHeli) ----
      put("zodiac", Profile.BOAT, 0.4F, true, RUBBER, 24);
      put("stinger_390x", Profile.BOAT, 0.7F, true);
      put("mark5", Profile.BOAT, 1.0F, true);
      put("cb90", Profile.BOAT, 1.1F, true);
      put("project1204", Profile.BOAT, 1.4F, false, STEEL_WELDED, 24);  // armoured river gunboat
      put("fuel_truck", Profile.TRUCK, 1.0F, true);
      // ---- emplacements ----
      put("25mmaamg", Profile.GUN, 0.6F, false);
      put("bofors40mml60", Profile.GUN, 0.8F, false);
      put("12.7", Profile.GUN, 1.0F, false);
      put("12.7s", Profile.GUN, 1.15F, false);
      put("15.5", Profile.GUN, 1.8F, false);
      put("46", Profile.GUN, 3.5F, false);
      put("oto76mm", Profile.GUN, 1.2F, true, CRT, 1);
      put("mk15", Profile.CIWS, 1.0F, true);
      put("vads", Profile.CIWS, 0.9F, true);
      put("searam", Profile.CIWS, 1.0F, true, BARREL_LIGHT, 0, PCB, 4);
      put("sea_sparrow", Profile.SAM, 1.0F, true);
      put("mim-23", Profile.SAM, 1.1F, true);
      put("s-75", Profile.SAM, 1.2F, true);
      put("mk32", Profile.TORPEDO, 0.8F, true);
      put("type93_launcher", Profile.TORPEDO, 1.0F, false);
      put("ammo_box", Profile.AMMO_BOX, 1.0F, true);
   }

   private MCH_VehicleAssembly() {}

   /** The bundled vehicles that have a hand-picked profile. */
   public static Set<String> bundledNames() {
      return Collections.unmodifiableSet(BUNDLED.keySet());
   }

   /**
    * The assembly recipe for a vehicle.
    *
    * @param category {@code helicopter}, {@code plane}, {@code tank} or {@code vehicle} (MCHeli's config folders)
    * @param maxHp    the config's {@code MaxHp}, used to size a vehicle that is not bundled
    * @param weapons  how many weapons it mounts
    */
   public static Recipe recipeFor(String name, String category, int maxHp, int weapons) {
      Entry e = BUNDLED.get(name.toLowerCase(Locale.ROOT));
      if (e == null) {
         e = guess(category, maxHp, weapons);
      }
      Builder b = new Builder(e.size);
      build(b, e.profile, e.modern, weapons > 0);
      e.changes.forEach(b::set);
      return b.recipe(baseDuration(e.profile), basePower(e.profile));
   }

   /** A profile for a content-pack vehicle, from what its config says. */
   private static Entry guess(String category, int maxHp, int weapons) {
      String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
      Profile p;
      float ref;
      switch (c) {
         case "tank", "tanks" -> {
            p = weapons == 0 ? Profile.CAR : maxHp >= 250 ? Profile.TANK_MODERN : Profile.WHEELED_AFV;
            ref = 300.0F;
         }
         case "helicopter", "helicopters" -> {
            p = weapons >= 6 ? Profile.HELI_ATTACK : maxHp >= 250 ? Profile.HELI_HEAVY
               : maxHp >= 150 ? Profile.HELI_UTILITY : Profile.HELI_LIGHT;
            ref = 180.0F;
         }
         case "plane", "planes" -> {
            p = maxHp >= 500 ? Profile.JET_HEAVY : weapons >= 6 ? Profile.JET : Profile.PROP;
            ref = p == Profile.JET_HEAVY ? 700.0F : 200.0F;
         }
         default -> {
            p = Profile.GUN;
            ref = 200.0F;
         }
      }
      float size = Math.max(0.5F, Math.min(2.0F, maxHp / ref));
      return new Entry(p, size, true, Map.of());
   }

   private static void build(Builder b, Profile p, boolean modern, boolean armed) {
      String circuit = modern ? CIRCUIT : ANALOG;
      switch (p) {
         case TANK_WW2 -> {
            b.bulk(STEEL, 32).bulk(STEEL_PLATE, 32).bulk(STEEL_CAST, 32).bulk(STEEL_WELDED, 16)
               .bulk(COPPER, 32).bulk(HSS, 28)
               .fixed(GEAR_TRAIN, 2).fixed(BARREL_HEAVY, 2).fixed(MOTOR, 2).fixed(ANALOG, 2);
         }
         case TANK_MODERN -> {
            b.bulk(STEEL, 36).bulk(STEEL_PLATE, 36).bulk(STEEL_WELDED, 32).bulk(STEEL_CAST, 32)
               .bulk(COPPER, 16).bulk(HSS, 32).bulk(TITANIUM_PLATE, 12)
               .fixed(GEAR_TRAIN, 2).fixed(CIRCUIT, 2).fixed(TATB, 10).fixed(BARREL_HEAVY_HSS, 2).fixed(MOTOR, 3);
         }
         case IFV -> {
            b.bulk(STEEL, 32).bulk(STEEL_PLATE, 32).bulk(STEEL_WELDED, 28).bulk(ALU_PLATE, 24)
               .bulk(COPPER, 12).bulk(HSS, 24)
               .fixed(GEAR_TRAIN, 2).fixed(CIRCUIT, 2).fixed(TATB, 6).fixed(BARREL_HEAVY_HSS, 1).fixed(MOTOR, 2)
               .fixed(MECHANISM, 1);
         }
         case WHEELED_AFV -> {
            b.bulk(STEEL, 28).bulk(STEEL_PLATE, 28).bulk(STEEL_WELDED, 24).bulk(STEEL_CAST, 16)
               .bulk(COPPER, 16).bulk(HSS, 24).bulk(RUBBER, 16)
               .fixed(GEAR_TRAIN, 2).fixed(circuit, 1).fixed(BARREL_HEAVY, 1).fixed(MOTOR, 2);
         }
         case LIGHT_ARMORED -> {
            b.bulk(STEEL, 16).bulk(STEEL_PLATE, 24).bulk(ALU_PLATE, 12).bulk(COPPER, 8).bulk(HSS, 8).bulk(RUBBER, 12)
               .fixed(GEAR_TRAIN, 1).fixed(MOTOR, 1).fixed(circuit, 1);
            if (armed) {
               b.fixed(BARREL_LIGHT, 1).fixed(MECHANISM, 1);
            }
         }
         case CAR -> {
            b.bulk(STEEL_PLATE, 16).bulk(ALU_PLATE, 12).bulk(COPPER, 8).bulk(RUBBER, 8).bulk(GLASS, 6)
               .fixed(MOTOR, 1).fixed(GEAR_TRAIN, 1).fixed(circuit, 1);
         }
         case TRUCK -> {
            b.bulk(STEEL, 16).bulk(STEEL_PLATE, 24).bulk(STEEL_WELDED, 8).bulk(RUBBER, 12).bulk(COPPER, 8)
               .fixed(MOTOR, 2).fixed(GEAR_TRAIN, 1).fixed(FLUID_TANK, 4).fixed(PIPE, 4);
         }
         case HELI_LIGHT -> {
            b.bulk(ALU, 16).bulk(ALU_PLATE, 16).bulk(STEEL, 8).bulk(COPPER, 8).bulk(GLASS, 8)
               .fixed(ALU_SHELL, 2).fixed(GEAR_TRAIN, 1).fixed(MOTOR, 1).fixed(CIRCUIT, 1);
         }
         case HELI_UTILITY -> {
            b.bulk(ALU, 24).bulk(ALU_PLATE, 32).bulk(ALU_WELDED, 24).bulk(ALU_CAST, 16).bulk(STEEL, 12)
               .bulk(COPPER, 16).bulk(TITANIUM_PLATE, 6)
               .fixed(ALU_SHELL, 4).fixed(GEAR_TRAIN, 1).fixed(MOTOR, 2)
               .fixed(CIRCUIT, 2);
         }
         case HELI_ATTACK -> {
            b.bulk(ALU, 24).bulk(ALU_PLATE, 32).bulk(ALU_WELDED, 24).bulk(ALU_CAST, 16).bulk(TITANIUM_PLATE, 16)
               .bulk(HSS, 12).bulk(COPPER, 12)
               .fixed(ALU_SHELL, 2).fixed(GEAR_TRAIN, 1).fixed(MOTOR, 2).fixed(CIRCUIT, 3)
               .fixed(BARREL_LIGHT, 1);
         }
         case HELI_HEAVY -> {
            b.bulk(ALU, 32).bulk(ALU_PLATE, 40).bulk(ALU_WELDED, 32).bulk(ALU_CAST, 24).bulk(STEEL, 16)
               .bulk(COPPER, 24).bulk(TITANIUM_PLATE, 12)
               .fixed(ALU_SHELL, 8).fixed(GEAR_TRAIN, 1).fixed(MOTOR, 4)
               .fixed(CIRCUIT, 2);
         }
         case HELI_DRONE -> {
            b.bulk(ALU_PLATE, 8).bulk(COPPER, 4)
               .fixed(ALU_SHELL, 1).fixed(GEAR_TRAIN, 1).fixed(MOTOR, 1).fixed(CIRCUIT, 1).fixed(PCB, 2);
         }
         case PROP -> {
            b.bulk(ALU, 16).bulk(ALU_PLATE, 24).bulk(ALU_CAST, 8).bulk(STEEL, 12).bulk(COPPER, 12).bulk(GLASS, 6)
               .fixed(ALU_SHELL, 1).fixed(MOTOR, 1).fixed(circuit, 1);
            if (armed) {
               b.fixed(BARREL_LIGHT, 1).fixed(MECHANISM, 1);
            }
         }
         case PROP_HEAVY -> {
            b.bulk(ALU, 32).bulk(ALU_PLATE, 48).bulk(ALU_WELDED, 32).bulk(ALU_CAST, 24).bulk(STEEL, 24)
               .bulk(COPPER, 24).bulk(GLASS, 8)
               .fixed(ALU_SHELL, 6).fixed(MOTOR, 4).fixed(circuit, 2);
            if (armed) {
               b.fixed(BARREL_LIGHT, 2).fixed(MECHANISM, 2);
            }
         }
         case JET -> {
            b.bulk(ALU, 24).bulk(ALU_PLATE, 32).bulk(ALU_WELDED, 16).bulk(TITANIUM, 16).bulk(TITANIUM_PLATE, 16)
               .bulk(HSS, 16)
               .fixed(ALU_SHELL, 4).fixed(CIRCUIT, 3).fixed(CRT, 1);
            if (armed) {
               b.fixed(BARREL_LIGHT, 1);
            }
         }
         case JET_STEALTH -> {
            b.bulk(ALU_PLATE, 24).bulk(ALU_WELDED, 16).bulk(TITANIUM, 24).bulk(TITANIUM_PLATE, 32)
               .bulk(TITANIUM_WELDED, 16).bulk(HSS, 24).bulk(POLYMER, 16)
               .fixed(ALU_SHELL, 4).fixed(CIRCUIT, 6).fixed(PCB, 4).fixed(CRT, 1);
         }
         case JET_HEAVY -> {
            b.bulk(ALU, 32).bulk(ALU_PLATE, 48).bulk(ALU_WELDED, 40).bulk(ALU_CAST, 24).bulk(TITANIUM_PLATE, 16)
               .bulk(HSS, 16).bulk(COPPER, 32).bulk(GLASS, 8)
               .fixed(ALU_SHELL, 10).fixed(CIRCUIT, 4).fixed(CRT, 1);
         }
         case PLANE_DRONE -> {
            b.bulk(ALU_PLATE, 12).bulk(ALU_CAST, 4).bulk(COPPER, 6)
               .fixed(ALU_SHELL, 1).fixed(MOTOR, 1).fixed(CIRCUIT, 2).fixed(PCB, 2);
         }
         case BOAT -> {
            b.bulk(STEEL_PLATE, 24).bulk(ALU_PLATE, 24).bulk(ALU_WELDED, 16).bulk(STEEL, 12).bulk(COPPER, 12)
               .bulk(RUBBER, 4)
               .fixed(MOTOR, 2).fixed(circuit, 1).fixed(PIPE, 4);
            if (armed) {
               b.fixed(BARREL_LIGHT, 1).fixed(MECHANISM, 1);
            }
         }
         case GUN -> {
            b.bulk(STEEL, 16).bulk(STEEL_PLATE, 16).bulk(STEEL_CAST, 16).bulk(HSS, 8).bulk(COPPER, 8)
               .fixed(GEAR_TRAIN, 1).fixed(MOTOR, 1).fixed(BARREL_HEAVY, 1).fixed(MECHANISM, 1).fixed(circuit, 1);
         }
         case CIWS -> {
            b.bulk(STEEL, 16).bulk(STEEL_PLATE, 16).bulk(ALU_PLATE, 16).bulk(HSS, 12).bulk(COPPER, 12)
               .fixed(MOTOR, 2).fixed(CIRCUIT, 3).fixed(CRT, 1).fixed(BARREL_LIGHT, 2).fixed(MECHANISM, 1);
         }
         case SAM -> {
            b.bulk(STEEL, 16).bulk(STEEL_PLATE, 24).bulk(ALU_PLATE, 16).bulk(COPPER, 16)
               .fixed(MOTOR, 1).fixed(CIRCUIT, 3).fixed(CRT, 1).fixed(PCB, 2).fixed(PIPE, 4);
         }
         case TORPEDO -> {
            b.bulk(STEEL, 12).bulk(STEEL_PLATE, 16).bulk(COPPER, 8).bulk(PIPE, 6)
               .fixed(MOTOR, 1).fixed(circuit, 1);
         }
         case AMMO_BOX -> {
            b.bulk(STEEL_PLATE, 16).bulk(STEEL, 8)
               .fixed("mcheli:ammo_bullet", 16).fixed("mcheli:ammo_tank_shell", 4)
               .fixed("mcheli:ammo_missile", 2).fixed("mcheli:ammo_bomb", 2);
         }
      }
   }

   /** Ticks at size 1 (the assembly machine's speed upgrades shorten it). */
   private static int baseDuration(Profile p) {
      return switch (p) {
         case TANK_MODERN, JET_STEALTH -> 3600;
         case TANK_WW2, IFV, JET, JET_HEAVY, HELI_ATTACK, HELI_HEAVY -> 2400;
         case WHEELED_AFV, HELI_UTILITY, PROP_HEAVY, SAM, CIWS -> 1800;
         default -> 1200;
      };
   }

   /** Power drawn per tick. */
   private static int basePower(Profile p) {
      return switch (p) {
         case TANK_MODERN, JET_STEALTH, JET_HEAVY -> 1000;
         case IFV, JET, HELI_ATTACK, HELI_HEAVY, SAM, CIWS -> 500;
         case TANK_WW2, WHEELED_AFV, HELI_UTILITY, PROP_HEAVY, GUN -> 250;
         default -> 100;
      };
   }

   private static final class Builder {
      private final float size;
      private final Map<String, Integer> inputs = new LinkedHashMap<>();

      Builder(float size) {
         this.size = size;
      }

      /** A structural material: scales with the vehicle's size. */
      Builder bulk(String ingredient, int base) {
         return set(ingredient, Math.max(1, Math.round(base * this.size)));
      }

      /** A component: the same count whatever the size. */
      Builder fixed(String ingredient, int count) {
         return set(ingredient, count);
      }

      Builder set(String ingredient, int count) {
         if (count <= 0) {
            this.inputs.remove(ingredient);
         } else {
            this.inputs.put(ingredient, Math.min(MAX_STACK, count));
         }
         return this;
      }

      Recipe recipe(int baseDuration, int power) {
         List<Input> list = new ArrayList<>();
         this.inputs.forEach((k, v) -> list.add(new Input(k, v)));
         if (list.size() > MAX_INPUTS) {
            throw new IllegalStateException("assembly recipe has " + list.size() + " inputs, max " + MAX_INPUTS);
         }
         int duration = Math.round(baseDuration * Math.max(0.5F, Math.min(2.0F, this.size)));
         return new Recipe(List.copyOf(list), duration, power);
      }
   }
}
