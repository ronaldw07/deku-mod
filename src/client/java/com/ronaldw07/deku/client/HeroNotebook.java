package com.ronaldw07.deku.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The notebook's pages: how to use every move. Keys are the defaults; they can be changed in Controls. */
final class HeroNotebook {
	private HeroNotebook() {
	}

	static List<Component> pages() {
		return List.of(
			page("Hero Analysis", """
				Slot 1: One For All
				Slot 2: Explosion
				Slot 4: Decay
				Slot 5: Half Cold Half Hot

				Hold a quirk item to use its moves. Its controls show in the bottom right.

				K opens settings.
				Change keys in
				Options > Controls."""),
			page("One For All", """
				C: Full Cowling on/off
				Hold V: charge Smash, let go to throw
				Z: Smokescreen, keep holding to make the cloud grow
				Hold R, or double-tap and hold space: Float
				B: Blackwhip
				Hold space on the ground: crouch and charge, let go to launch toward your crosshair
				Space in the air: air flick (hold to keep flicking)"""),
			page("Shoot Style", """
				X: St. Louis Smash, a kick that throws a crescent of wind. At half power and up it slices through terrain.

				Y: Delaware Smash, a finger flick. The air bullet hits every mob in its line and bursts where it lands.

				G: Manchester Smash. Leap, flip and axe kick the ground. In the air it dives straight down.

				N: Gearshift on/off. Keep moving to shift up to gear 5."""),
			page("United States of Smash", """
				U: rocket into the sky, hang there winding up, then dive fist first.

				Leaves a huge crater with a ring cut around it, and a tornado that spins in the middle for a minute."""),
			page("One For All tips", """
				Smash locks onto the mob nearest your crosshair.

				Blackwhip: aim at a mob to pull it in, or at a block to pull yourself there."""),
			page("Danger Sense", """
				Works with any item. H turns it on/off.

				Yellow lightning shows where danger is coming from. Big bolts mean it's very close."""),
			page("Explosion", """
				Tap right-click: big AP Shot
				Hold right-click: rapid fire
				Double-tap space, keep holding: fly (WASD to move)
				Hold V: Howitzer Impact, let go to explode
				Hold C: charge ground blast, let go
				X: Cluster Bomb
				Z: Explosion Cowling on/off, bigger blasts"""),
			page("Decay", """
				Right-click: decay what you touch. It crumbles and the decay spreads through everything connected to it.

				Hold V: wind up, let go to slam the ground and send a wave of decay rolling out.

				Mobs it reaches rot away."""),
			page("Decay at full power", """
				Hold X: Catastrophe. Wind up for 3 seconds, let go, and everything all around you decays, out to 128 blocks.

				C: Decay Cowling on/off. Faster, stronger, higher jumps, and whatever you run into crumbles."""),
			page("Half Cold Half Hot", """
				Right-click: ice spikes race along the ground, impaling and freezing mobs.
				Hold V: flamethrower. It melts ice too.
				Hold space: ice slide.
				C: a giant wall of ice.
				X: Flashfreeze Heatwave. Ice, then a huge blast of fire."""),
			page("Settings (K)", """
				Full Cowling power and ramp-up time.

				Smash power and charge time.

				Danger Sense on/off.

				Cooldowns on/off."""));
	}

	private static Component page(String title, String body) {
		return Component.literal(title).withStyle(ChatFormatting.BOLD)
			.append(Component.literal("\n\n" + body).withStyle(style -> style.withBold(false)));
	}
}
