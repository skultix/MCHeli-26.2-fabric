# Playing with NTM (HBM's Nuclear Tech Mod)

MCHeli works on its own, but with [NTM](https://modrinth.com/) for 26.2 (`hbm`) installed, vehicles and their ammunition
are built with NTM's machines. Nothing needs configuring; MCHeli detects NTM at start-up.

## Ammunition

Every weapon reloads only from its own ammunition, paid per round. The reload button in the vehicle menu fills the
weapon to its maximum, or as far as your ammo goes. The number under the ammo icon is what a full reload costs: white
when you have it, yellow when you have enough for part of it, red when you have none. Each item's tooltip says what it
holds.

| Item | Used by | Rounds per item |
|---|---|---|
| Vehicle Bullets (`mcheli:ammo_bullet`) | Machine guns up to 12.7 mm | a belt of 25 |
| Autocannon Rounds (`mcheli:ammo_autocannon`) | 20–40 mm cannons: M61 Vulcan, GAU-8, M230, 2A42, Bofors… | a belt of 20 |
| 40mm Grenade (`mcheli:ammo_grenade`) | Grenade launchers: Mk19, GMW, M75 | 1 |
| Tank Shell (`mcheli:ammo_tank_shell`) | Tank guns | 1 |
| Artillery Shell (`mcheli:ammo_artillery_shell`) | Howitzers, naval guns and their shells | 1 |
| Mortar Bomb (`mcheli:ammo_mortar`) | Mortars | 1 |
| Guided Missile (`mcheli:ammo_missile`) | Air-to-air, anti-tank, TV-guided and air-to-surface missiles, guided bombs | 1 |
| Rocket (`mcheli:ammo_rocket`) | Unguided rockets | 1 |
| Bomb (`mcheli:ammo_bomb`) | Unguided bombs | 1 |
| Torpedo (`mcheli:ammo_torpedo`) | Torpedoes | 1 |

So 8 rockets cost 8 Rockets, and 200 rounds for an M230 cost 10 belts of Autocannon Rounds. When only part of the
last belt is needed, the whole belt is used.

Dispensers, smoke, targeting pods and other weapons that fire no ammunition keep their config's own items. A weapon's
ammunition is worked out from its config (`mcheli.agnostic.weapon.MCH_AmmoClass`): its type, its name, and for guns
the calibre in its name, or its power and supply size when the name has none.

The ammo is made in NTM's **ammo press**:

| Output | Inputs |
|---|---|
| 4 Vehicle Bullets | 2 steel ingots, 1 cordite, 1 small gunmetal casing |
| 1 Autocannon Rounds | 3 steel ingots, 2 cordite, 1 large gunmetal casing |
| 4 40mm Grenades | 2 steel ingots, 1 TATB, 1 small gunmetal casing |
| 1 Tank Shell | 5 steel ingots, 4 TATB, 1 large gunmetal casing |
| 1 Artillery Shell | 6 steel ingots, 5 TATB, 2 cordite, 1 large weapon steel casing |
| 2 Mortar Bombs | 3 steel ingots, 2 TATB, 1 cordite |
| 1 Guided Missile | 4 steel ingots, 2 aluminium ingots, 1 printed circuit board, 4 TATB, 3 solid fuel |
| 2 Rockets | 2 steel ingots, 1 aluminium ingot, 2 TATB, 2 solid fuel |
| 1 Bomb | 4 steel ingots, 2 aluminium ingots, 4 TATB |
| 1 Torpedo | 6 steel ingots, 3 aluminium ingots, 6 TATB, 1 motor |

Without NTM, the items have crafting-table recipes instead (`data/mcheli/recipe/ammo_fallback/`).

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
