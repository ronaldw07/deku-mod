# Deku Mod

Fabric mod for Minecraft 26.2: One For All and Explosion quirks.

New players get One For All, Explosion, a notebook of instructions, Decay, Half Cold Half Hot, Gojo and Sukuna in hotbar slots 1-7.
Keep inventory is always on, so dying never drops your items.
Hold a quirk item to use its moves; its controls and cooldowns show in the bottom right. Keys can be changed in Options > Controls. M is the Form key: it switches on Full Cowling, Explosion Cowling, Decay Cowling, Infinity or Demon Arms, whichever quirk you hold.

## One For All (slot 1)

- M: Full Cowling on/off
- Hold V: charge Smash, release to throw (100% blasts through terrain)
- Z: Smokescreen; keep holding and the cloud keeps growing, up to 30 blocks wide
- Hold R, or double-tap and hold space: Float
- B: Blackwhip
- Hold space on the ground: crouch and charge, release to launch toward the crosshair
- Space in the air: flick a blast of air to push yourself where you look; hold to keep flicking
- X: Shoot Style, a St. Louis Smash kick that throws a crescent of wind (slices terrain from 50%)
- Hold Y: Delaware Smash, wind up a finger flick for up to 2 seconds (a quick tap is weaker), let go to fire an air bullet through every mob in its line, bursting into a crater
- U: United States of Smash, rocket into the sky and dive fist first: a crater 240 blocks across with a ring cut around it, and a 150 block tornado that spins in the middle for a minute
- G: Manchester Smash, leap, flip and axe kick the ground into a crater and shockwave
- N: Gearshift on/off, shifts up to gear 5 the longer you keep moving
- J: screen shake and flash on/off
- K: settings (power, ramp-up and charge times, Smash max range, Danger Sense, cooldowns on/off, particle detail, screen shake)

## Explosion (slot 2)

- Tap right-click: big AP Shot. Hold: rapid fire
- Double-tap and hold space: fly (W/A/S/D to move and lie flat, hover standing otherwise; hold sprint to boost to double speed with a sonic boom)
- Hold V: Howitzer Impact, red lightning crackles out of you as you spin; a quick release is half size, hold 3 seconds for full size, under a mushroom cloud
- Hold C: charge the cross-arm ground blast, release to fire
- Hold X: Cluster Bomb, grow a red fireball in front of you for up to 3 seconds, let go to throw it at the crosshair (150 blocks): it lands as a nuke with a huge crater and a mushroom cloud
- M: Explosion Cowling on/off, a red-orange glow (other players see it too) that makes every blast 25% bigger and stronger

Explosions burn out as a white-hot flash, a fireball that goes orange, red and then black, and thick black smoke that hangs for ten seconds or more. Big blasts set off secondary pops, fling burning chunks, send a dust ring racing over the ground, scorch the crater rim black and shake the screen when they go off close by. A Howitzer Impact raises a mushroom cloud over 100 blocks tall with ash falling over the area. Particle detail and screen shake can be turned down or off in the K menu.

## Decay (slot 4)

- Right-click: decay what you touch; it crumbles and the decay spreads through everything connected to it
- Hold V: wind up, release to slam the ground and send a wave of decay rolling out (up to 64 blocks)
- Hold X: Catastrophe, a 3 second wind up, then everything all around decays out to 128 blocks, mountains included
- M: Decay Cowling on/off, faster, stronger, higher jumps, and whatever you run into crumbles
- Mobs it reaches rot away

## Half Cold Half Hot (slot 5)

- Right-click: a glacier of ice spikes races along the ground, impaling, freezing and trapping anyone it erupts into
- Hold V: flamethrower, a 30 block cone of fire that burns and shoves mobs, sets the ground ablaze and melts ice
- Hold space: ice slide, glide where you look on a bridge of ice that builds under your feet
- C: a giant wall of ice erupts ahead, hurling and freezing anyone in the way
- X: Flashfreeze Heatwave, a 28 block dome of ice freezes the area ahead, cracks glow through it, then it blows apart in a huge fire and steam nova with a crater, flame pillar, magma rain and a mushroom cloud

## Gojo (slot 6)

- Hold right-click: Blue, a blue sphere lands at the crosshair and drags everything nearby into it, rips up blocks; hold up to 3 seconds for a bigger sphere with a pull radius out to 48 blocks
- Hold V: Red, charge up to 2 seconds and throw a red sphere that bursts and hurls everything away
- Hold X: Hollow Purple, a blue and a red orb draw together over 3 seconds into a purple orb; let go and it erases everything in a line 150 blocks long, spraying lightning and leaving a trail of dark purple fire that burns and zaps everything beside the path, then collapses
- M: Infinity on/off, nothing that touches you can hurt you and anything close is pushed away
- C: Domain Expansion (Infinite Void), a 40 block black ball of stars where everything but you is lifted into the air and frozen for 20 seconds

## Sukuna (slot 7)

- Right-click: Dismantle, a flurry of huge black-and-white slashes ahead (70 blocks long, 36 tall) that erase every block and entity in their path
- V: Cleave, one heavy cut on whatever the crosshair is on, a third of a mob's health on top of the hit
- M: Demon Arms on/off, four extra arms; cuts hit 50% harder and Dismantle throws more slashes
- Hold X: Fuga, draw a burning arrow for up to 2.5 seconds and loose it at the crosshair; it lands as a huge fire blast that leaves a column of molten light standing in the sky for half a minute, and sets everything near it alight
- C: Domain Expansion (Malevolent Shrine), a 120 block dome. A real shrine of blocks (platform, red pillars, tiered black roof, gate) is built under you and removed when it ends; the camera flies out to the side for a short cutscene while endless slashes shred the terrain and everything inside it for 10 seconds

## The Villain

A boss to fight: spawn it with the Villain Spawn Egg (Spawn Eggs creative tab, or `/summon deku:villain`). It has 400 health and a boss bar. Up close it winds up a ground slam, from a distance it throws a ball of red light, and if you back away it leaps after you.

## Hero costumes

Deku, Bakugo and Todoroki armor sets, four pieces each. Cosmetic only: no protection, never wears out. Craft a leather piece with a dye (green for Deku, orange for Bakugo, red and white for Todoroki) or find them in the Combat creative tab.

## Danger Sense

Always on with any item; H toggles it. Yellow lightning on screen shows where danger is coming from.

## Custom sounds

Every sound is a `deku:` sound event built from vanilla sounds in `assets/deku/sounds.json`.
A resource pack can replace any of them with its own `.ogg` files.

## Build

Requires Java 25.

```
./gradlew build             # jar in build/libs/
./gradlew runClientGameTest # launches the game, exercises every move, saves screenshots
```
