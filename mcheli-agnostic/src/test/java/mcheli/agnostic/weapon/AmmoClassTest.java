package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AmmoClassTest {
   private static final Path WEAPONS = Path.of("..", "mcheli-assets", "assets", "mcheli", "weapons");

   /** The bundled weapons, with only the fields the classifier reads. */
   private static Map<String, MCH_WeaponInfo> bundled() throws IOException {
      Map<String, MCH_WeaponInfo> out = new TreeMap<>();
      try (Stream<Path> files = Files.list(WEAPONS)) {
         for (Path f : (Iterable<Path>) files::iterator) {
            String file = f.getFileName().toString();
            if (!file.endsWith(".txt")) {
               continue;
            }
            MCH_WeaponInfo wi = new MCH_WeaponInfo(file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT));
            for (String line : Files.readAllLines(f, StandardCharsets.ISO_8859_1)) {
               int eq = line.indexOf('=');
               if (eq < 0) {
                  continue;
               }
               String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
               if (key.equals("type") || key.equals("displayname") || key.equals("power")) {
                  wi.loadItemData(key, line.substring(eq + 1).trim());
               }
            }
            out.put(wi.name, wi);
         }
      }
      return out;
   }

   @Test
   void bundledWeaponsGetTheRightAmmo() throws IOException {
      Map<String, MCH_WeaponInfo> w = bundled();
      Map<String, MCH_AmmoClass> expect = new TreeMap<>(Map.ofEntries(
         // up to 12.7 mm: bullets, by calibre or (no calibre in the name) by power
         Map.entry("m2_rws", MCH_AmmoClass.BULLET), Map.entry("mg12_7mm", MCH_AmmoClass.BULLET),
         Map.entry("m134", MCH_AmmoClass.BULLET), Map.entry("mg_coaxial", MCH_AmmoClass.BULLET),
         Map.entry("yakb-12_7", MCH_AmmoClass.BULLET), Map.entry("gshg7_62mm", MCH_AmmoClass.BULLET),
         Map.entry("mg15", MCH_AmmoClass.BULLET), Map.entry("mg3_l", MCH_AmmoClass.BULLET),
         // larger guns: tank shells
         Map.entry("m230", MCH_AmmoClass.SHELL), Map.entry("m61a1", MCH_AmmoClass.SHELL),
         Map.entry("gau-8", MCH_AmmoClass.SHELL), Map.entry("2a46m-1_apfsds", MCH_AmmoClass.SHELL),
         Map.entry("rehinmetall_he", MCH_AmmoClass.SHELL), Map.entry("152mm_howitzer", MCH_AmmoClass.SHELL),
         Map.entry("15_5cm", MCH_AmmoClass.SHELL), Map.entry("46cm", MCH_AmmoClass.SHELL),
         Map.entry("gpu-2", MCH_AmmoClass.SHELL), Map.entry("a10gau8", MCH_AmmoClass.SHELL),
         // gun-fired "Rocket"-type rounds
         Map.entry("type3bullet_46", MCH_AmmoClass.SHELL), Map.entry("81mm_mortar", MCH_AmmoClass.SHELL),
         Map.entry("mk19_l", MCH_AmmoClass.SHELL), Map.entry("gmw", MCH_AmmoClass.SHELL),
         // guided: missiles
         Map.entry("aim9", MCH_AmmoClass.MISSILE), Map.entry("agm114", MCH_AmmoClass.MISSILE),
         Map.entry("9m119m", MCH_AmmoClass.MISSILE), Map.entry("gbu-12", MCH_AmmoClass.MISSILE),
         // unguided, not gun-fired: bombs
         Map.entry("mk82", MCH_AmmoClass.BOMB), Map.entry("hydra70", MCH_AmmoClass.BOMB),
         Map.entry("s-8", MCH_AmmoClass.BOMB), Map.entry("mk46", MCH_AmmoClass.BOMB),
         // no ammunition
         Map.entry("dispenser_water", MCH_AmmoClass.NONE), Map.entry("smoke_red", MCH_AmmoClass.NONE),
         Map.entry("targeting_pod_block", MCH_AmmoClass.NONE)));
      for (Map.Entry<String, MCH_AmmoClass> e : expect.entrySet()) {
         MCH_WeaponInfo wi = w.get(e.getKey());
         assertTrue(wi != null, "no bundled weapon " + e.getKey());
         assertEquals(e.getValue(), MCH_AmmoClass.classify(wi), e.getKey() + " (" + wi.displayName + ")");
      }
      Map<MCH_AmmoClass, Integer> counts = new EnumMap<>(MCH_AmmoClass.class);
      w.values().forEach(wi -> counts.merge(MCH_AmmoClass.classify(wi), 1, Integer::sum));
      System.out.println("bundled weapons by ammo class: " + counts);
   }

   @Test
   void caliberParsing() {
      assertEquals(12.7F, MCH_AmmoClass.caliberMm("RWS M2 12.7mm"));
      assertEquals(155.0F, MCH_AmmoClass.caliberMm("15.5cm Type91[AP]"));
      assertEquals(105.0F, MCH_AmmoClass.caliberMm("105mmgun"));
      assertTrue(Float.isNaN(MCH_AmmoClass.caliberMm("M134 Minigun")));
   }

   @Test
   void applyReplacesTheCostWithOneAmmoItem() {
      MCH_WeaponInfo gun = new MCH_WeaponInfo("test_gun");
      gun.type = "MachineGun2";
      gun.displayName = "125mm KBA-3 [HE]";
      gun.loadItemData("item", "2, iron_ingot");
      gun.loadItemData("item", "3, gunpowder");
      MCH_WeaponInfo pump = new MCH_WeaponInfo("test_pump");
      pump.type = "Dispenser";
      pump.loadItemData("item", "1, water_bucket");
      assertEquals(1, MCH_AmmoClass.apply(List.of(gun, pump)));
      assertEquals(1, gun.roundItems.size());
      assertEquals("mcheli:ammo_tank_shell", gun.roundItems.get(0).itemName);
      assertEquals(1, gun.roundItems.get(0).num);
      assertEquals("water_bucket", pump.roundItems.get(0).itemName);
   }
}
