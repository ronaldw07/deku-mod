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

				Hold a quirk item to use its moves. Its controls show in the bottom right.

				K opens settings.
				Change keys in
				Options > Controls."""),
			page("One For All", """
				C: Full Cowling on/off
				Hold V: charge Smash, let go to throw
				Z: Smokescreen
				Hold R, or double-tap and hold space: Float
				B: Blackwhip
				Hold space on the ground: crouch and charge, let go to launch toward your crosshair
				Space in the air: air flick (hold to keep flicking)"""),
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
				X: Cluster Bomb"""),
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
