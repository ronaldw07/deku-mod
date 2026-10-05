package com.ronaldw07.deku;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Cosmetic hero costumes: Deku, Bakugo and Todoroki, each a full set that gives no protection
 * and never wears out. Craft a leather piece with the set's dye, or find them in the Combat tab.
 */
public final class DekuArmor {
	private static final ArmorType[] PIECES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final List<Item> ALL = new ArrayList<>();

	public static final List<Item> DEKU = set("deku");
	public static final List<Item> BAKUGO = set("bakugo");
	public static final List<Item> TODOROKI = set("todoroki");

	private DekuArmor() {
	}

	static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> ALL.forEach(entries::accept));
	}

	private static List<Item> set(String name) {
		ArmorMaterial material = new ArmorMaterial(1, ArmorMaterials.makeDefense(0, 0, 0, 0, 0), 1, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F,
			ItemTags.REPAIRS_LEATHER_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, DekuMod.id(name)));
		List<Item> pieces = new ArrayList<>();
		for (ArmorType type : PIECES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DekuMod.id(name + "_" + type.getName()));
			Item item = new Item(new Item.Properties().humanoidArmor(material, type).component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
				.rarity(Rarity.RARE).setId(key));
			pieces.add(Registry.register(BuiltInRegistries.ITEM, key, item));
		}
		ALL.addAll(pieces);
		return List.copyOf(pieces);
	}
}
