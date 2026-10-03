# Deku Mod

Fabric mod for Minecraft 26.2: One For All and Explosion quirks.

New players get One For All, Explosion and a notebook of instructions in hotbar slots 1-3.
Hold a quirk item to use its moves; its controls and cooldowns show in the bottom right. Keys can be changed in Options > Controls.

## One For All (slot 1)

- C: Full Cowling on/off
- Hold V: charge Smash, release to throw (100% blasts through terrain)
- Z: Smokescreen
- Hold R, or double-tap and hold space: Float
- B: Blackwhip
- K: settings (power, ramp-up and charge times, Danger Sense, cooldowns on/off)

## Explosion (slot 2)

- Tap right-click: big AP Shot. Hold: rapid fire
- Double-tap and hold space: fly (W/A/S/D to move, hover otherwise)
- Hold V: Howitzer Impact, release to explode
- Hold C: charge the cross-arm ground blast, release to fire

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
