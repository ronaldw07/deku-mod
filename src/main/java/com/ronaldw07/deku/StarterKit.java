package com.ronaldw07.deku;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hands each player One For All, Explosion, the notebook and Decay in hotbar slots 1-4 the first
 * time they join a world.
 */
public final class StarterKit {
	private static final AttachmentType<Boolean> RECEIVED = AttachmentRegistry.create(DekuMod.id("received_starter_kit"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	private static final AttachmentType<Boolean> RECEIVED_DECAY = AttachmentRegistry.create(DekuMod.id("received_decay"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	private static final Item[] KIT = {DekuItems.ONE_FOR_ALL, DekuItems.EXPLOSION, DekuItems.HERO_NOTEBOOK, DekuItems.DECAY};
	private static final int DECAY_SLOT = 3;

	private StarterKit() {
	}

	public static void giveOnFirstJoin(ServerPlayer player) {
		if (!player.getAttachedOrElse(RECEIVED, false)) {
			for (int slot = 0; slot < KIT.length; slot++) {
				give(player, KIT[slot], slot);
			}
			player.setAttached(RECEIVED, true);
			player.setAttached(RECEIVED_DECAY, true);
		} else if (!player.getAttachedOrElse(RECEIVED_DECAY, false)) {
			// Decay came later, so players who already had the kit get it on their next join.
			give(player, DekuItems.DECAY, DECAY_SLOT);
			player.setAttached(RECEIVED_DECAY, true);
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
