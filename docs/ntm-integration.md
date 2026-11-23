# Playing with NTM (HBM's Nuclear Tech Mod)

MCHeli works on its own, but with [NTM](https://modrinth.com/) for 26.2 (`hbm`) installed, vehicles and their ammunition
are built with NTM's machines. Nothing needs configuring; MCHeli detects NTM at start-up.

## Ammunition

Every weapon reloads only from its own ammunition, paid per round. The reload button in the vehicle menu fills the
weapon to its maximum, or as far as your ammo goes. The number under the ammo icon is what a full reload costs: white
when you have it, yellow when you have enough for part of it, red when you have none. Each item's tooltip says what it
holds, and all of them are in the **MCHeli: Ammunition** creative tab.

**Guns use rounds of their own calibre.** There is one item per calibre (`mcheli:ammo_20mm` "20mm Rounds",
`mcheli:ammo_105mm` "105mm Shell", and so on), so a 30 mm cannon takes only 30mm Rounds. Calibres up to 40 mm come as
belts of 25 rounds; bigger guns take one shell per item. 40 mm grenades and mortar bombs have their own items, apart from
40 mm cannon rounds. The bundled vehicles use 22 calibres:

| Items | Rounds per item | Used by, for example |
|---|---|---|
| 7.62mm, 7.7mm, 7.92mm Rounds | belt of 25 | M134, M240, coaxial MGs, Lewis, MG 15 |
| 12.7mm Rounds | belt of 25 | M2, M3M, YakB-12.7 |
| 20mm, 23mm, 25mm, 27mm, 30mm, 40mm Rounds | belt of 25 | M61 Vulcan, M197, GSh-23, GAU-12, BK-27, M230, GAU-8, 2A42, Bofors |
| 40mm Grenades | belt of 25 | Mk19, GMW, M75 |
| 76mm, 90mm, 105mm, 120mm, 125mm Shell | 1 | tank guns, the M102 howitzer, the OTO Melara |
| 127mm, 152mm, 155mm, 460mm Shell | 1 | naval guns, howitzers and their AA shells |
| 60mm, 81mm Mortar Bomb | 1 | mortars |

A content pack's gun with a new calibre gets its own item automatically. The calibre is read from the weapon's name
("M2 12.7mm", "15.5cm Type91"); a pack gun that names none gets a typical calibre for its kind.

**Missiles, rockets, bombs and torpedoes share one item each**, one round per item: Guided Missile
(`mcheli:ammo_missile`, also guided bombs), Rocket (`mcheli:ammo_rocket`), Bomb (`mcheli:ammo_bomb`) and Torpedo
(`mcheli:ammo_torpedo`).

So 8 rockets cost 8 Rockets, and 200 rounds for an M230 cost 8 belts of 30mm Rounds. When only part of the last belt
is needed, the whole belt is used. Dispensers, smoke, targeting pods and other weapons that fire no ammunition keep
their config's own items. The rules live in `mcheli.agnostic.weapon.MCH_AmmoClass` and `MCH_AmmoCaliber`.

### Making ammunition

Calibre rounds are made in NTM's **ammo press**. Their cost scales with the size of the round: about calibre^2.5,
close to real cartridge weights, set so one 125 mm shell costs 5 steel, 4 TATB, 2 cordite and a large gunmetal
casing. Small rounds come several belts to a press. Some examples:

| Output | Inputs |
|---|---|
| 16 × 7.62mm Rounds (400 rounds) | 2 steel ingots, 1 cordite, 1 small gunmetal casing |
| 4 × 12.7mm Rounds (100 rounds) | 2 steel ingots, 1 cordite, 1 small gunmetal casing |
| 1 × 20mm Rounds | 1 steel ingot, 1 TATB, 1 cordite, 1 large gunmetal casing |
| 1 × 30mm Rounds | 4 steel ingots, 3 TATB, 1 cordite, 1 large gunmetal casing |
| 1 × 40mm Rounds | 7 steel ingots, 6 TATB, 3 cordite, 1 large gunmetal casing |
| 1 × 40mm Grenades | 2 steel ingots, 1 TATB, 1 cordite, 1 small gunmetal casing |
| 1 × 81mm Mortar Bomb | 1 steel ingot, 1 TATB, 1 cordite |
| 1 × 105mm Shell | 3 steel ingots, 3 TATB, 1 cordite, 1 large gunmetal casing |
| 1 × 125mm Shell | 5 steel ingots, 4 TATB, 2 cordite, 1 large gunmetal casing |
| 1 × 155mm Shell | 9 steel ingots, 7 TATB, 3 cordite, 1 large weapon steel casing |
| 1 × 460mm Shell | 130 steel ingots, 104 TATB, 52 cordite, 1 large weapon steel casing |

The shared items:

| Output | Inputs |
|---|---|
| 1 Guided Missile | 4 steel ingots, 2 aluminium ingots, 1 printed circuit board, 4 TATB, 3 solid fuel |
| 2 Rockets | 2 steel ingots, 1 aluminium ingot, 2 TATB, 2 solid fuel |
| 1 Bomb | 4 steel ingots, 2 aluminium ingots, 4 TATB |
| 1 Torpedo | 6 steel ingots, 3 aluminium ingots, 6 TATB, 1 motor |

Without NTM, every ammo item has a crafting-table recipe instead: the same recipe in vanilla materials (iron for steel,
TNT for TATB, gunpowder for cordite, a copper ingot for a casing), trimmed to fit the grid for the biggest shells.

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
