package com.ronaldw07.deku;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The quirk items: hold one to use its moves. */
public final class DekuItems {
	public static final Item ONE_FOR_ALL = register("one_for_all", Item::new);
	public static final Item EXPLOSION = register("explosion", Item::new);
	public static final Item HERO_NOTEBOOK = register("hero_notebook", HeroNotebookItem::new);
	public static final Item DECAY = register("decay", Item::new);
	public static final Item HALF_COLD_HALF_HOT = register("half_cold_half_hot", Item::new);
	public static final Item GOJO = register("gojo", Item::new);
	public static final Item SUKUNA = register("sukuna", Item::new);

	private DekuItems() {
	}

	/** Forces the static fields above to register. */
	static void init() {
	}

	public static boolean isHolding(Player player, Item item) {
		return player.getMainHandItem().getItem() == item;
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DekuMod.id(name));
		Item item = factory.apply(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
