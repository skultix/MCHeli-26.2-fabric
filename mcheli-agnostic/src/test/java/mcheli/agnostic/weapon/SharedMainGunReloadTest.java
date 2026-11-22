package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import org.junit.jupiter.api.Test;

/** A tank's main-gun rounds share one reload; its machine guns and other mounts keep their own. */
class SharedMainGunReloadTest {
   private static MCH_WeaponInfo weapon(String name, String type, String displayName, int reload) {
      MCH_WeaponInfo w = new MCH_WeaponInfo(name);
      w.loadItemData("type", type);
      w.loadItemData("displayname", displayName);
      w.loadItemData("power", "100");
      w.loadItemData("round", "1");
      w.loadItemData("delay", "5");
      w.loadItemData("reloadtime", String.valueOf(reload));
      return w;
   }

   @Test
   void mainGunRoundsShareTheReload() {
      MCH_AircraftInfo t90 = new mcheli.agnostic.tank.MCH_TankInfo("test_t90");
      // As in t-90.txt: three cannon rounds, a gun-launched ATGM and the coaxial MG on one mount; plus a roof MG.
      t90.loadItemData("addweapon", "test_apfsds, 0,1.61,0.44, 0,0, true,1");
      t90.loadItemData("addweapon", "test_he, 0,1.61,0.44, 0,0, true,1");
      t90.loadItemData("addweapon", "test_atgm, 0,1.61,0.44, 0,0, true,1");
      t90.loadItemData("addweapon", "test_coax, 0,1.61,0.44, 0,0, true,1");
      t90.loadItemData("addweapon", "test_roof, 0.5,2.5,0, 0,0, true,1");
      MCH_WeaponInfo apfsds = weapon("test_apfsds", "MachineGun1", "2A46M-1 Smoothbore Gun[APFSDS]", 120);
      MCH_WeaponInfo he = weapon("test_he", "MachineGun2", "2A46M-1 Smoothbore Gun[HE]", 120);
      MCH_WeaponInfo atgm = weapon("test_atgm", "TVMissile", "9M119M", 200);
      MCH_WeaponInfo coax = weapon("test_coax", "MachineGun1", "Coaxial Machinegun", 40);
      coax.power = 8; // a rifle-calibre MG (no calibre in its name)
      MCH_WeaponInfo roof = weapon("test_roof", "MachineGun2", "25mm Machine gun", 40);
      java.util.Map<String, MCH_WeaponInfo> all = java.util.Map.of(apfsds.name, apfsds, he.name, he, atgm.name, atgm,
         coax.name, coax, roof.name, roof);
      VehicleWeapons w = VehicleWeapons.build(t90, all::get, 2);
      assertEquals(5, w.size());
      WeaponSlot sApfsds = find(w, "test_apfsds");
      WeaponSlot sHe = find(w, "test_he");
      WeaponSlot sAtgm = find(w, "test_atgm");
      WeaponSlot sCoax = find(w, "test_coax");
      WeaponSlot sRoof = find(w, "test_roof");
      w.all().forEach(s -> assertTrue(s.canFire(), s.weaponName + " starts ready"));

      sApfsds.fireOneShot();
      w.shareMainGunCooldown(sApfsds);
      assertFalse(sHe.canFire(), "HE shares the gun's reload");
      assertFalse(sAtgm.canFire(), "the gun-launched ATGM shares the gun's reload");
      assertEquals(120, sHe.readyInTicks());
      assertTrue(sHe.reloadFraction() > 0.9F, "the held round shows as reloading");
      assertTrue(sCoax.canFire(), "the coaxial machine gun keeps its own timer");
      assertTrue(sRoof.canFire(), "a weapon on another mount keeps its own timer");

      for (int i = 0; i < 120; i++) {
         w.tick();
      }
      assertTrue(sHe.canFire(), "the shared reload has run out");

      sAtgm.fireOneShot();
      w.shareMainGunCooldown(sAtgm);
      assertEquals(200, sApfsds.readyInTicks(), "the ATGM's longer reload holds the cannon rounds too");
   }

   private static WeaponSlot find(VehicleWeapons w, String name) {
      return w.all().stream().filter(s -> s.weaponName.equals(name)).findFirst().orElseThrow();
   }
}
