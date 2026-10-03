package com.ronaldw07.deku;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Hands each player One For All, Explosion and the notebook in hotbar slots 1-3 the first time they join a world. */
public final class StarterKit {
	private static final AttachmentType<Boolean> RECEIVED = AttachmentRegistry.create(DekuMod.id("received_starter_kit"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	private static final Item[] KIT = {DekuItems.ONE_FOR_ALL, DekuItems.EXPLOSION, DekuItems.HERO_NOTEBOOK};

	private StarterKit() {
	}

	public static void giveOnFirstJoin(ServerPlayer player) {
		if (player.getAttachedOrElse(RECEIVED, false)) {
			return;
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < KIT.length; slot++) {
			ItemStack stack = new ItemStack(KIT[slot]);
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else if (!inventory.add(stack)) {
				player.drop(stack, false);
			}
		}
		player.setAttached(RECEIVED, true);
	}
}
