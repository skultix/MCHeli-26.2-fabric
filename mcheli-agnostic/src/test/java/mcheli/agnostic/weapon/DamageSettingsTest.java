package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import mcheli.agnostic.spi.Role;
import org.junit.jupiter.api.Test;

class DamageSettingsTest {
   private static MCH_WeaponInfo weapon(String name, String type, int power, int explosion, int explosionBlock) {
      MCH_WeaponInfo wi = new MCH_WeaponInfo(name);
      wi.type = type;
      wi.power = power;
      wi.explosion = explosion;
      wi.explosionBlock = explosionBlock;
      return wi;
   }

   @Test
   void neutralDefaultsChangeNothing() {
      MCH_DamageSettings s = MCH_DamageSettings.defaults();
      MCH_WeaponInfo wi = weapon("test_neutral", "Rocket", 20, 3, 3);
      s.apply(List.of(wi));
      assertEquals(20, wi.power);
      assertEquals(3, wi.explosion);
      for (Role r : Role.values()) {
         assertEquals(1.0F, s.directMultiplier(wi, r));
         assertEquals(1.0F, s.explosionMultiplier(wi, r));
      }
   }

   @Test
   void multipliersStack() {
      MCH_DamageSettings s = MCH_DamageSettings.defaults();
      s.directDamage = 2.0F;
      s.explosionDamage = 0.5F;
      s.weaponTypes.put("MachineGun2", new MCH_DamageSettings.Multipliers(3.0F, null));
      s.vsTarget.put("tank", 0.25F);
      MCH_DamageSettings.WeaponOverride o = new MCH_DamageSettings.WeaponOverride();
      o.directDamage = 1.5F;
      o.explosionDamage = 4.0F;
      s.weapons.put("M230_Test", o);
      MCH_WeaponInfo wi = weapon("m230_test", "machinegun2", 16, 0, 0);
      assertEquals(2.0F * 3.0F * 1.5F, s.directMultiplier(wi, Role.PLAYER), 1e-6);
      assertEquals(2.0F * 3.0F * 1.5F * 0.25F, s.directMultiplier(wi, Role.TANK), 1e-6);
      assertEquals(0.5F * 4.0F, s.explosionMultiplier(wi, Role.HELICOPTER), 1e-6);
      // An unknown round still gets the global and per-target multipliers.
      assertEquals(2.0F * 0.25F, s.directMultiplier(null, Role.TANK), 1e-6);
   }

   @Test
   void overridesReplaceConfigValuesAndReloadsDoNotStack() {
      MCH_WeaponInfo wi = weapon("test_missile", "ATMissile", 62, 4, 4);
      wi.damageFactor = new MCH_DamageFactor();
      wi.damageFactor.add(Role.PLAYER, 0.5F);

      MCH_DamageSettings s = MCH_DamageSettings.defaults();
      MCH_DamageSettings.WeaponOverride o = new MCH_DamageSettings.WeaponOverride();
      o.power = 100;
      o.explosion = 6;
      o.damageFactor = Map.of("tank", 2.0F);
      s.weapons.put("test_missile", o);
      s.apply(List.of(wi));
      assertEquals(100, wi.power);
      assertEquals(6, wi.explosion);
      assertEquals(6, wi.explosionBlock, "ExplosionBlock followed Explosion in the config, so it follows the override");
      assertEquals(2.0F, wi.damageFactor.getDamageFactor(Role.TANK));
      assertEquals(0.5F, wi.damageFactor.getDamageFactor(Role.PLAYER), "unlisted categories keep the config factor");

      // Reload with the override removed: back to the config values.
      MCH_DamageSettings.defaults().apply(List.of(wi));
      assertEquals(62, wi.power);
      assertEquals(4, wi.explosion);
      assertEquals(4, wi.explosionBlock);
      assertEquals(1.0F, wi.damageFactor.getDamageFactor(Role.TANK));
      assertEquals(62, MCH_DamageSettings.original(wi).power);
   }

   @Test
   void explicitExplosionBlockIsKept() {
      MCH_WeaponInfo wi = weapon("test_bomb", "Bomb", 30, 5, 2);
      MCH_DamageSettings s = MCH_DamageSettings.defaults();
      MCH_DamageSettings.WeaponOverride o = new MCH_DamageSettings.WeaponOverride();
      o.explosion = 8;
      s.weapons.put("test_bomb", o);
      s.apply(List.of(wi));
      assertEquals(8, wi.explosion);
      assertEquals(2, wi.explosionBlock);
   }

   @Test
   void validateFlagsBadValues() {
      MCH_DamageSettings s = MCH_DamageSettings.defaults();
      s.directDamage = -1.0F;
      s.vsTarget.put("boat", 2.0F);
      List<String> w = s.validate();
      assertEquals(0.0F, s.directDamage);
      assertFalse(w.isEmpty());
      assertEquals(2, w.size(), w.toString());
      assertNull(MCH_DamageSettings.role("boat"));
   }
}
