package com.ronaldw07.deku;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hands each player One For All, Explosion, the notebook, Decay and Half Cold Half Hot in hotbar
 * slots 1-5 the first time they join a world. Quirks added in later updates go to players who
 * already had the kit on their next join.
 */
public final class StarterKit {
	private static final AttachmentType<Boolean> RECEIVED = received("received_starter_kit");
	private static final Item[] KIT = {DekuItems.ONE_FOR_ALL, DekuItems.EXPLOSION, DekuItems.HERO_NOTEBOOK};

	/** A quirk that came after the original kit, the slot it belongs in, and whether this player has had it yet. */
	private record Addition(Item item, int slot, AttachmentType<Boolean> received) {
	}

	private static final Addition[] ADDITIONS = {
		new Addition(DekuItems.DECAY, 3, received("received_decay")),
		new Addition(DekuItems.HALF_COLD_HALF_HOT, 4, received("received_half_cold_half_hot")),
	};

	private StarterKit() {
	}

	private static AttachmentType<Boolean> received(String name) {
		return AttachmentRegistry.create(DekuMod.id(name), builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	}

	public static void giveOnFirstJoin(ServerPlayer player) {
		if (!player.getAttachedOrElse(RECEIVED, false)) {
			for (int slot = 0; slot < KIT.length; slot++) {
				give(player, KIT[slot], slot);
			}
			player.setAttached(RECEIVED, true);
		}
		for (Addition addition : ADDITIONS) {
			if (!player.getAttachedOrElse(addition.received(), false)) {
				give(player, addition.item(), addition.slot());
				player.setAttached(addition.received(), true);
			}
		}
	}

	/** Puts the item in the slot if it's free, anywhere else in the inventory if not, or at the player's feet. */
	private static void give(ServerPlayer player, Item item, int slot) {
		Inventory inventory = player.getInventory();
		ItemStack stack = new ItemStack(item);
		if (inventory.getItem(slot).isEmpty()) {
			inventory.setItem(slot, stack);
		} else if (!inventory.add(stack)) {
			player.drop(stack, false);
		}
	}
}
