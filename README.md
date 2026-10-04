# Deku Mod

Fabric mod for Minecraft 26.2: One For All and Explosion quirks.

New players get One For All, Explosion, a notebook of instructions, Decay and Half Cold Half Hot in hotbar slots 1-5.
Keep inventory is always on, so dying never drops your items.
Hold a quirk item to use its moves; its controls and cooldowns show in the bottom right. Keys can be changed in Options > Controls.

## One For All (slot 1)

- C: Full Cowling on/off
- Hold V: charge Smash, release to throw (100% blasts through terrain)
- Z: Smokescreen
- Hold R, or double-tap and hold space: Float
- B: Blackwhip
- Hold space on the ground: crouch and charge, release to launch toward the crosshair
- Space in the air: flick a blast of air to push yourself where you look; hold to keep flicking
- X: Shoot Style, a St. Louis Smash kick that throws a crescent of wind (slices terrain from 50%)
- Y: Delaware Smash, a finger flick that fires an air bullet through every mob in its line, bursting into a small crater
- U: United States of Smash, rocket into the sky and dive fist first: a huge crater with a ring cut around it, and a tornado that spins in the middle for a minute
- G: Manchester Smash, leap, flip and axe kick the ground into a crater and shockwave
- N: Gearshift on/off, shifts up to gear 5 the longer you keep moving
- K: settings (power, ramp-up and charge times, Danger Sense, cooldowns on/off)

## Explosion (slot 2)

- Tap right-click: big AP Shot. Hold: rapid fire
- Double-tap and hold space: fly (W/A/S/D to move, hover otherwise)
- Hold V: Howitzer Impact, release to explode
- Hold C: charge the cross-arm ground blast, release to fire
- X: Cluster Bomb, a 100-block line of red bombs going off one after another

## Decay (slot 4)

- Right-click: decay what you touch; it crumbles and the decay spreads through everything connected to it
- Hold V: wind up, release to slam the ground and send a wave of decay rolling out (up to 64 blocks)
- Hold X: Catastrophe, a 3 second wind up, then everything all around decays out to 128 blocks, mountains included
- C: Decay Cowling on/off, faster, stronger, higher jumps, and whatever you run into crumbles
- Mobs it reaches rot away

## Half Cold Half Hot (slot 5)

- Right-click: a glacier of ice spikes races along the ground, freezing and trapping mobs
- Hold V: flamethrower, which sets mobs and the ground on fire and melts ice
- C: a giant wall of ice erupts ahead
- X: Flashfreeze Heatwave, a block of ice blown apart by a blast of heat

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
