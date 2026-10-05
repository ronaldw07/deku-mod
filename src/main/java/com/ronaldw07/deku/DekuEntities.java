package com.ronaldw07.deku;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The villain boss and the egg that spawns it. */
public final class DekuEntities {
	private static final int VILLAIN_TRACKING_RANGE = 10;

	public static final EntityType<Villain> VILLAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE, DekuMod.id("villain"),
		EntityType.Builder.<Villain>of(Villain::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(VILLAIN_TRACKING_RANGE)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, DekuMod.id("villain"))));
	public static final Item VILLAIN_SPAWN_EGG = spawnEgg("villain_spawn_egg", VILLAIN);

	private DekuEntities() {
	}

	static void init() {
		FabricDefaultAttributeRegistry.register(VILLAIN, Villain.createVillainAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(VILLAIN_SPAWN_EGG));
	}

	private static Item spawnEgg(String name, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DekuMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().spawnEgg(type).rarity(Rarity.EPIC).setId(key)));
	}
}
