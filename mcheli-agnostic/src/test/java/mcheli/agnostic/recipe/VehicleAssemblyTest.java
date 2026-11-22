package mcheli.agnostic.recipe;

import static mcheli.agnostic.recipe.MCH_VehicleAssembly.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class VehicleAssemblyTest {
   private static final Path ASSETS = Path.of("..", "mcheli-assets", "assets", "mcheli");
   private static final String[] CATEGORIES = {"helicopters", "planes", "tanks", "vehicles"};

   /** vehicle name -> category folder, for every bundled vehicle config. */
   private static Map<String, String> bundled() throws IOException {
      Map<String, String> out = new TreeMap<>();
      for (String c : CATEGORIES) {
         try (Stream<Path> files = Files.list(ASSETS.resolve(c))) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".txt"))
               .forEach(n -> out.put(n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT), c));
         }
      }
      return out;
   }

   private static Recipe r(String name) {
      return recipeFor(name, "", 200, 1);
   }

   private static int steel(Recipe r) {
      return r.count(STEEL) + r.count(STEEL_PLATE) + r.count(STEEL_WELDED) + r.count(STEEL_CAST);
   }

   private static int aluminium(Recipe r) {
      return r.count(ALU) + r.count(ALU_PLATE) + r.count(ALU_WELDED) + r.count(ALU_CAST);
   }

   @Test
   void everyBundledVehicleHasAHandPickedRecipeThatFitsTheMachine() throws IOException {
      Map<String, String> all = bundled();
      assertEquals(112, all.size());
      Set<String> missing = new HashSet<>(all.keySet());
      missing.removeAll(bundledNames());
      assertTrue(missing.isEmpty(), "no profile for " + missing);
      Set<String> signatures = new HashSet<>();
      for (String name : all.keySet()) {
         Recipe rec = r(name);
         assertTrue(rec.inputs().size() <= MAX_INPUTS, name + " has " + rec.inputs().size() + " inputs");
         for (Input in : rec.inputs()) {
            assertTrue(in.count() >= 1 && in.count() <= MAX_STACK, name + ": " + in);
         }
         assertTrue(rec.durationTicks() > 0 && rec.power() > 0, name);
         signatures.add(rec.inputs().toString());
      }
      assertTrue(signatures.size() > 90, "recipes should vary, only " + signatures.size() + " distinct");
   }

   @Test
   void tanksFollowTheBrief() {
      for (String t : new String[] {"kv-2", "m26", "m1a2", "merkava_mk4", "t-90", "t84", "kurganets25", "centauro", "m1129"}) {
         Recipe rec = r(t);
         assertEquals(2, rec.count(GEAR_TRAIN), t + " gear trains");
         assertTrue(steel(rec) >= 80 && steel(rec) <= 160, t + " steel " + steel(rec));
         assertTrue(rec.count(HSS) >= 20 && rec.count(HSS) <= 40, t + " HSS " + rec.count(HSS));
      }
      // early tanks: about half a stack of copper
      assertTrue(r("m26").count(COPPER) >= 28 && r("kv-2").count(COPPER) <= 40);
      // modern tanks: integrated circuits and about 10 TATB, and dearer than the early ones
      for (String t : new String[] {"m1a2", "merkava_mk4", "t-90", "t84"}) {
         Recipe rec = r(t);
         assertTrue(rec.count(CIRCUIT) >= 1, t);
         assertTrue(rec.count(TATB) >= 8 && rec.count(TATB) <= 14, t + " TATB");
         assertTrue(steel(rec) > steel(r("m26")), t + " should cost more steel than the M26");
         assertTrue(rec.durationTicks() > r("m26").durationTicks());
      }
      assertEquals(0, r("m26").count(CIRCUIT));
      assertTrue(steel(r("kv-2")) > steel(r("m26")), "the KV-2 is the bigger tank");
   }

   @Test
   void helicoptersAreAluminiumWithOneGearTrain() {
      for (String h : new String[] {"ah-64", "uh-1c", "ch47", "bell47g", "mi-24", "rc-goblin"}) {
         Recipe rec = r(h);
         assertEquals(1, rec.count(GEAR_TRAIN), h);
         assertTrue(rec.count(CIRCUIT) >= 1, h + " needs integrated circuits");
         assertTrue(rec.count(ALU_SHELL) >= 1, h + " needs aluminium shells");
         assertTrue(aluminium(rec) > steel(rec), h + " is mostly aluminium");
      }
      assertTrue(aluminium(r("ah-64")) >= 90, "an attack helicopter takes about as much aluminium as a tank does steel");
      assertTrue(aluminium(r("ch47")) > aluminium(r("bell47g")));
   }

   @Test
   void jetsNeedTitaniumHssCircuitsAndShells() {
      for (String j : new String[] {"f-15e", "mig29", "a-10", "f22a", "f-35b", "b-2a", "c5", "t-4"}) {
         Recipe rec = r(j);
         assertTrue(rec.count(TITANIUM) + rec.count(TITANIUM_PLATE) + rec.count(TITANIUM_WELDED) > 0, j + " titanium");
         assertTrue(rec.count(HSS) > 0, j + " HSS");
         assertTrue(rec.count(CIRCUIT) > 0, j + " circuits");
         assertTrue(rec.count(ALU_SHELL) > 0, j + " shells");
      }
      // propeller aircraft are the other kind: no titanium
      for (String p : new String[] {"p-51d", "cessna172", "b29"}) {
         assertEquals(0, r(p).count(TITANIUM) + r(p).count(TITANIUM_PLATE), p);
      }
      assertTrue(r("f22a").count(CIRCUIT) > r("f-15e").count(CIRCUIT), "stealth fighters are harder");
   }

   @Test
   void contentPackVehiclesGetARecipeFromTheirCategory() {
      for (String c : new String[] {"tank", "helicopter", "plane", "vehicle"}) {
         for (int hp : new int[] {10, 200, 3000}) {
            Recipe rec = recipeFor("pack_" + c + hp, c, hp, 4);
            assertTrue(!rec.inputs().isEmpty() && rec.inputs().size() <= MAX_INPUTS, c + " " + hp);
            rec.inputs().forEach(i -> assertTrue(i.count() >= 1 && i.count() <= MAX_STACK));
         }
      }
      assertEquals(2, recipeFor("pack_mbt", "tank", 400, 3).count(GEAR_TRAIN));
   }
}
