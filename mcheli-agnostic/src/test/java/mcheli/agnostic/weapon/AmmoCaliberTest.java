package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Every gun reloads from rounds of its own calibre, costed by round size. */
class AmmoCaliberTest {
   private static MCH_AmmoCaliber round(float mm) {
      return new MCH_AmmoCaliber(mm, MCH_AmmoCaliber.Kind.ROUND);
   }

   @Test
   void bundledGunsUseTheirOwnCalibre() throws IOException {
      Map<String, MCH_WeaponInfo> w = AmmoClassTest.bundled();
      Map<String, String> expect = new TreeMap<>(Map.ofEntries(
         Map.entry("m134", "ammo_7_62mm"), Map.entry("mg_coaxial", "ammo_7_62mm"), Map.entry("lewis7_7mm", "ammo_7_7mm"),
         Map.entry("mg15", "ammo_7_92mm"), Map.entry("m2_rws", "ammo_12_7mm"), Map.entry("yakb-12_7", "ammo_12_7mm"),
         Map.entry("m61a1", "ammo_20mm"), Map.entry("m197", "ammo_20mm"), Map.entry("gsh-23-2", "ammo_23mm"),
         Map.entry("gau-12u", "ammo_25mm"), Map.entry("bk27", "ammo_27mm"), Map.entry("m230", "ammo_30mm"),
         Map.entry("gau-8", "ammo_30mm"), Map.entry("a10gau8", "ammo_30mm"), Map.entry("2a42", "ammo_30mm"),
         Map.entry("bofors40mm", "ammo_40mm"), Map.entry("mk19_l", "ammo_40mm_grenade"),
         Map.entry("gmw", "ammo_40mm_grenade"), Map.entry("d56ts", "ammo_76mm"), Map.entry("76mmgun", "ammo_76mm"),
         Map.entry("90mm_gun_m3", "ammo_90mm"), Map.entry("105mm", "ammo_105mm"), Map.entry("m102_105mm", "ammo_105mm"),
         Map.entry("rehinmetall_he", "ammo_120mm"), Map.entry("canistershell", "ammo_120mm"),
         Map.entry("2a46m-1_apfsds", "ammo_125mm"), Map.entry("kba-3_he", "ammo_125mm"),
         Map.entry("12_7cm", "ammo_127mm"), Map.entry("type3bullet", "ammo_127mm"),
         Map.entry("152mm_howitzer", "ammo_152mm"), Map.entry("15_5cm", "ammo_155mm"),
         Map.entry("type3bullet_15_5", "ammo_155mm"), Map.entry("46cm", "ammo_460mm"),
         Map.entry("type3bullet_46", "ammo_460mm"), Map.entry("60mm_mortar", "ammo_60mm_mortar"),
         Map.entry("81mm_mortar", "ammo_81mm_mortar")));
      Map<String, String> got = new TreeMap<>();
      for (String name : expect.keySet()) {
         MCH_AmmoCaliber c = MCH_AmmoClass.caliber(w.get(name));
         got.put(name, c == null ? null : c.itemPath());
      }
      assertEquals(expect, got);
      // Missiles, rockets, bombs and torpedoes are not calibre items.
      for (String name : List.of("aim9", "hydra70", "mk82", "mk46")) {
         assertNull(MCH_AmmoClass.caliber(w.get(name)), name);
      }
      SortedSet<MCH_AmmoCaliber> all = MCH_AmmoClass.calibers(w.values());
      System.out.println("bundled calibre items: " + all.stream().map(MCH_AmmoCaliber::itemPath).toList());
      assertEquals(22, all.size());
   }

   @Test
   void labelsAndBelts() {
      assertEquals("7.62mm", round(7.62F).label());
      assertEquals("ammo_7_62mm", round(7.62F).itemPath());
      assertEquals("ammo_105mm", round(105.0F).itemPath());
      assertEquals(25, round(7.62F).roundsPerItem());
      assertEquals(25, round(40.0F).roundsPerItem());
      assertEquals(1, round(76.0F).roundsPerItem());
      assertEquals(25, new MCH_AmmoCaliber(40.0F, MCH_AmmoCaliber.Kind.GRENADE).roundsPerItem());
      assertEquals(1, new MCH_AmmoCaliber(60.0F, MCH_AmmoCaliber.Kind.MORTAR).roundsPerItem());
      assertEquals("item.mcheli.ammo_rounds", round(30.0F).nameKey());
      assertEquals("item.mcheli.ammo_shell", round(120.0F).nameKey());
   }

   /** Steel per round, from the press recipe. */
   private static double steelPerRound(MCH_AmmoCaliber c) {
      MCH_AmmoCaliber.Cost cost = c.pressCost();
      int steel = 0;
      for (MCH_AmmoCaliber.Ingredient i : cost.inputs()) {
         steel += i.id().equals(MCH_AmmoCaliber.STEEL) ? i.count() : i.id().equals(MCH_AmmoCaliber.STEEL_BLOCK) ? 9 * i.count() : 0;
      }
      return (double) steel / (cost.output() * c.roundsPerItem());
   }

   @Test
   void costScalesWithRoundSize() throws IOException {
      // One steel and one TATB per 10 mm, two cordite: a 120 mm shell is 12, 12 and 24.
      assertEquals("[Ingredient[id=#c:ingots/steel, count=12], Ingredient[id=hbm:ball_tatb, count=12], "
         + "Ingredient[id=hbm:cordite, count=24], Ingredient[id=hbm:casing_large, count=1]]",
         round(120.0F).pressCost().inputs().toString());
      assertEquals(1, round(120.0F).pressCost().output());
      // Bigger rounds never cost less per round (the 7.7/7.92 pair is within rounding).
      List<MCH_AmmoCaliber> rounds = new ArrayList<>();
      for (MCH_AmmoCaliber c : MCH_AmmoClass.calibers(AmmoClassTest.bundled().values())) {
         if (c.kind() == MCH_AmmoCaliber.Kind.ROUND && c.mm() >= 12.7F) {
            rounds.add(c);
         }
      }
      for (int i = 1; i < rounds.size(); i++) {
         assertTrue(steelPerRound(rounds.get(i)) >= steelPerRound(rounds.get(i - 1)),
            rounds.get(i).label() + " is cheaper per round than " + rounds.get(i - 1).label());
      }
      assertEquals(46.0, steelPerRound(round(460.0F)));
      // Small rounds come many to a press.
      assertEquals(2, round(7.62F).pressCost().output());
   }

   @Test
   void everyRecipeFits() throws IOException {
      for (MCH_AmmoCaliber c : MCH_AmmoClass.calibers(AmmoClassTest.bundled().values())) {
         MCH_AmmoCaliber.Cost press = c.pressCost();
         assertTrue(press.inputs().size() <= MCH_AmmoCaliber.SLOTS, c.itemPath());
         assertTrue(press.inputs().stream().allMatch(i -> i.count() >= 1 && i.count() <= 64), c.itemPath());
         MCH_AmmoCaliber.Cost craft = c.craftCost();
         int items = craft.inputs().stream().mapToInt(MCH_AmmoCaliber.Ingredient::count).sum();
         assertTrue(items >= 1 && items <= 9, c.itemPath() + " needs " + items + " crafting slots");
         System.out.println(c.itemPath() + ": press x" + press.output() + " " + press.inputs().stream()
            .map(i -> i.count() + " " + i.id()).collect(Collectors.joining(", ")) + " | craft x" + craft.output() + " "
            + craft.inputs().stream().map(i -> i.count() + " " + i.id()).collect(Collectors.joining(", ")));
      }
   }
}
