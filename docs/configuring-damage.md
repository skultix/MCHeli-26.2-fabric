# Configuring weapon damage

Every damage value of MCHeli's vehicle weapons can be tuned in one server-side file, `config/mcheli-damage.json`.
You don't need to touch the weapon `.txt` configs inside the jar.

- **Where:** the `config/` folder of the server, or of the game instance in singleplayer. For example, in Prism it
  is `<instance>/minecraft/config/mcheli-damage.json`.
- **Created:** on first start, with every value neutral (1.0, no overrides). It changes nothing until you edit it.
- **Applied:** at startup, and again when an operator runs **`/mcheli damage reload`**. Problems such as a typo in a
  weapon name, an unknown target or a negative number are logged and reported to whoever ran the command. If the
  file is not valid JSON, the previous settings stay in effect until it is fixed.
- **Who needs it:** only the server. Clients joining a server don't need a copy, and their own copy is ignored there.

Next to it, MCHeli rewrites **`config/mcheli-damage-defaults.json`** on every load. It lists every loaded weapon
(bundled and content-pack) with its type and its unmodified damage values, in the same shape as a `weapons` entry
below. Look up a weapon's name and numbers there and copy what you want to change. Edits to that file are ignored.

## How weapon damage is worked out

A weapon round can hurt something in two ways: by hitting it directly, and with its explosion. A missile that hits a
tank does both. A machine-gun round with `Explosion = 1` (like the M230) does both too.

```
direct hit = power × damageFactor(target)
                   × directDamage × weaponTypes[type].directDamage × weapons[name].directDamage × vsTarget[target]

explosion  = blast × damageFactor(target)
                   × explosionDamage × weaponTypes[type].explosionDamage × weapons[name].explosionDamage × vsTarget[target]
```

- **power** is the weapon's `Power`.
- **blast** comes from the weapon's `Explosion` (or `ExplosionInWater` underwater). It reaches entities within
  `2 × Explosion` blocks and does `floor((t² + t) / 2 × 8 × radius + 1)`, where
  `t = (1 − distance / radius) × the share of the target the blast can see`. A target at the centre of an
  `Explosion = 4` blast takes about 65; one at half the radius takes about 25.
- **damageFactor** is the weapon's own `DamageFactor` per target category (default 1). An AGM-114, for example, does
  double damage to tanks. FAE weapons ignore it for their blast, as in the original mod.

Every multiplier is optional and defaults to 1. Set one to 0 to turn that damage off.

When the target is an MCHeli vehicle, the result then goes through that vehicle's own armour, from its vehicle
config:
1. The hit is capped at `ArmorMaxDamage`.
2. If the struck hitbox is an armoured part (damage factor below 1), its factor is applied.
3. It is multiplied by `ArmorDamageFactor`.
4. `ArmorMinDamage` is subtracted.
5. If the struck hitbox is a weak point (damage factor above 1), its factor is applied.

That is why the same rocket does less to a tank than to a helicopter. Armour belongs to the vehicle, not the
weapon, so it is not in this file (see [What this file does not cover](#what-this-file-does-not-cover)).

## The file

```json
{
  "directDamage": 1.0,
  "explosionDamage": 1.0,
  "weaponTypes": {
    "MachineGun1": { "directDamage": 1.0, "explosionDamage": 1.0 },
    "Rocket":      { "directDamage": 1.0, "explosionDamage": 1.0 }
  },
  "vsTarget": { "player": 1.0, "living": 1.0, "other": 1.0,
                "helicopter": 1.0, "plane": 1.0, "tank": 1.0, "vehicle": 1.0 },
  "weapons": {
    "m230": { "power": 12, "damageFactor": { "player": 0.5 } }
  },
  "tacz": { "smallArmsFactor": 0.5, "smallArmsVsArmoredFactor": 0.0, "armoredMinDamage": 5.0 }
}
```

(The generated file lists all twelve weapon types. The example above is shortened.) Any section can be left out.
Names of weapons, weapon types and targets are case-insensitive.

### `directDamage`, `explosionDamage`

These multiply every direct hit, or the entity damage of every explosion, from every MCHeli weapon. A global
`explosionDamage` does not change how big explosions are or how many blocks they break. To change that, use
`explosion` / `explosionBlock` on a weapon.

### `weaponTypes`

Multipliers per weapon `Type`. Each entry takes `directDamage` and/or `explosionDamage`.

| Type | What it is |
|---|---|
| `MachineGun1`, `MachineGun2` | Guns and cannons. Tank main guns (`105mm`, `2A46M-1_APFSDS`, …) are `MachineGun2` too. |
| `Rocket` | Unguided rockets (Hydra 70, S-8, …). |
| `MkRocket` | Marker rockets. Their called-in airstrike bombs count as this weapon. |
| `ASMissile`, `ATMissile`, `TVMissile` | Air-to-surface, anti-tank and TV-guided missiles. |
| `AAMissile` | Air-to-air missiles. |
| `Bomb` | Bombs, including cluster bomblets. |
| `Torpedo` | Torpedoes. |
| `CAS` | Called-in close air support. Its bombs count as this weapon. |
| `Dispenser` | Item dispensers. Normally no damage. |

`Smoke`, `Dummy` and `TargetingPod` do no damage. Content-pack weapons with other types can be added by name.

Because tank cannons are `MachineGun2`, a `MachineGun2` multiplier also changes tank guns. To change only one of
them, use `weapons` instead.

### `vsTarget`

A multiplier per kind of target, applied to direct hits and explosions alike. It replaces the original mod's
`DamageVsPlayer` / `DamageVsLiving` / `DamageVsMCHeliTank` / … settings in `mcheli.cfg`.

| Target | Covers |
|---|---|
| `player` | Players. |
| `living` | Every other living entity (mobs, animals, villagers, armour stands). |
| `other` | Everything else (item frames, boats, minecarts, …). |
| `helicopter`, `plane`, `tank`, `vehicle` | MCHeli vehicles of that category. "vehicle" is the ground-vehicle category (`vehicles/` configs). |

### `weapons`

Overrides for one weapon, keyed by its name. The name is the weapon's file name without `.txt`, as listed in
`mcheli-damage-defaults.json` (`m230`, `hydra70`, `agm114`, `2a46m-1_apfsds`, …). Every field
is optional.

| Field | Replaces | Notes |
|---|---|---|
| `power` | `Power` | Direct-hit damage. |
| `explosion` | `Explosion` | Blast strength. Sets the damage radius (2 × this) and the blast damage. Range 0–50; 0 = no explosion. |
| `explosionBlock` | `ExplosionBlock` | How strongly the explosion breaks blocks (still subject to the `mobGriefing` gamerule). Range 0–50. If the weapon config leaves it at "same as Explosion", it follows your `explosion` override. In the defaults file, `-1` means "same as Explosion". |
| `explosionInWater` | `ExplosionInWater` | Blast strength when it goes off in water. 0 = use `explosion`. |
| `damageFactor` | `DamageFactor` | Map of target -> factor, using the target names from `vsTarget`. Only the categories you list are replaced; the others keep the weapon config's values. |
| `directDamage`, `explosionDamage` | — | Extra multipliers for this weapon only. |

Overrides always start from the weapon's config values. Reloading with an override removed restores the original
value; reloading twice never stacks.

### `tacz`

These rules only apply when [TaCZ Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated) is installed, for its
guns hitting MCHeli vehicles. Explosive TaCZ ammo (RPG, grenade launchers, the HE ammo mod) always does its full
designed damage and is not affected by these.

| Field | Default | Meaning |
|---|---|---|
| `smallArmsFactor` | 0.5 | Plain TaCZ bullets against unarmoured vehicles do this fraction of their damage. |
| `smallArmsVsArmoredFactor` | 0.0 | The same against armoured vehicles. 0 = immune. |
| `armoredMinDamage` | 5.0 | A vehicle counts as armoured when its config `ArmorMinDamage` is at least this. With 5, that is the real tanks (M1A2, T-90, Merkava, KV-2, …), but not the Growler, BNR32 or MXT-MV. |

## Examples

Halve the direct damage of every gun and cannon (against everything):

```json
{ "weaponTypes": { "MachineGun1": { "directDamage": 0.5 }, "MachineGun2": { "directDamage": 0.5 } } }
```

Halve only what particular guns do to players. `vsTarget.player` would do it for every weapon at once:

```json
{ "weapons": { "m230": { "damageFactor": { "player": 0.5 } }, "gau-8": { "damageFactor": { "player": 0.5 } } } }
```

Tougher tanks against everything, but the anti-tank missiles keep their punch:

```json
{
  "vsTarget": { "tank": 0.5 },
  "weaponTypes": { "ATMissile": { "directDamage": 2.0, "explosionDamage": 2.0 } }
}
```

Bigger Hydra rockets (larger blast radius, more block damage, more damage):

```json
{ "weapons": { "hydra70": { "explosion": 4, "explosionBlock": 2 } } }
```

Explosions hurt entities but break no blocks, server-wide: set `explosionBlock: 0` on each weapon, or turn off the
`mobGriefing` gamerule, which MCHeli explosions respect.

TaCZ rifles do a little damage to tanks too:

```json
{ "tacz": { "smallArmsVsArmoredFactor": 0.1 } }
```

## What this file does not cover

- **Vehicle armour and HP** (`MaxHp`, `ArmorDamageFactor`, `ArmorMinDamage`, `ArmorMaxDamage`, per-part
  `BoundingBox` damage factors) are in the vehicle configs. Content packs can add new vehicles and weapons, but they
  cannot replace a bundled config of the same name; the bundled one wins.
- Damage from **crashing, ramming and running mobs over**, and the explosion of a **destroyed vehicle**, are not
  weapon damage and are not scaled here.
- **Non-MCHeli** damage to vehicles (vanilla TNT, arrows, other mods) is not scaled here, apart from the TaCZ rules
  above.
