# Playing with NTM (HBM's Nuclear Tech Mod)

MCHeli works on its own, but with [NTM](https://modrinth.com/) for 26.2 (`hbm`) installed, vehicles and their ammunition
are built with NTM's machines. Nothing needs configuring; MCHeli detects NTM at start-up.

## Ammunition

Every weapon reloads from one of four ammo items. One item buys one supply: the weapon's `SuppliedNum` rounds (the
reload button in the vehicle menu, as before).

| Item | Used by |
|---|---|
| Vehicle Bullets (`mcheli:ammo_bullet`) | Guns up to 12.7 mm |
| Tank Shell (`mcheli:ammo_tank_shell`) | Guns larger than 12.7 mm (autocannons, tank guns, howitzers), and gun-fired shells, mortars and grenade launchers |
| Guided Missile (`mcheli:ammo_missile`) | Guided ordnance: air-to-air, anti-tank, TV-guided and air-to-surface missiles, guided bombs |
| Bomb (`mcheli:ammo_bomb`) | Unguided, non-gun ordnance: bombs, rockets, torpedoes (MCHeli's run straight) |

Dispensers, smoke, targeting pods and other weapons that fire no ammunition keep their config's own items. A weapon's
class is worked out from its config (`mcheli.agnostic.weapon.MCH_AmmoClass`): its type, and for guns the calibre in its
name, or its power when the name has none.

The ammo is made in NTM's **ammo press**:

| Output | Inputs |
|---|---|
| 4 Vehicle Bullets | 2 steel ingots, 1 cordite, 1 small gunmetal casing |
| 1 Tank Shell | 5 steel ingots, 4 TATB, 1 large gunmetal casing |
| 1 Guided Missile | 4 steel ingots, 2 aluminium ingots, 1 printed circuit board, 4 TATB, 3 solid fuel |
| 1 Bomb | 4 steel ingots, 2 aluminium ingots, 4 TATB |

Without NTM, the four items have crafting-table recipes instead (`data/mcheli/recipe/ammo_fallback/`).

## Vehicles

With NTM, every vehicle is built in NTM's **assembly machine**, and the crafting-table recipes from the vehicle configs
are switched off. The recipes are generated at start-up by `mcheli.agnostic.recipe.MCH_VehicleAssembly`. Each bundled
vehicle has a profile (WW2 tank, modern MBT, attack helicopter, jet, stealth jet, propeller plane, boat, emplacement…) and
a size based on the real vehicle. The profile sets the materials, and the structural ones scale with the size.

Everything comes from NTM's early-to-midgame: steel, industrial copper, aluminium, titanium, high-speed steel, welded and
cast plates, Aluminium Shells (`hbm:shell_aluminum`), motors, gear trains, integrated circuits and TATB.

- **Tanks:** two gear trains, about two stacks of steel (ingots, plates, welded and cast plates) depending on size,
  and about 30 high-speed steel. WW2 tanks (KV-2, M26) take about half a stack of copper and analog circuits. Modern
  MBTs add integrated circuits, titanium plate, high-speed steel barrels and 8–14 TATB for ERA, and take longer and
  more power to build.
- **Helicopters:** one gear train, aluminium in place of the steel, Aluminium Shells and integrated circuits. Attack
  helicopters add titanium armour and high-speed steel.
- **Jets:** titanium, high-speed steel, integrated circuits, Aluminium Shells and a CRT display (radar). Stealth jets
  need more circuits and titanium, plus polymer.
- **Propeller planes:** aluminium and steel, no titanium.

Content-pack vehicles get a profile from their category, hit points and armament.

To see a vehicle's recipe, use JEI, or the assembly machine's recipe selector (search for the vehicle's name).

## Explosions

NTM's nukes and big explosions destroy tanks, and its anti-tank mine does 50 damage to one. See the `ntm` section of
[configuring-damage.md](configuring-damage.md).
