package com.dirtworld;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
	public static Item DIRT_TOKEN;
	public static Block SHOP_PORTAL;
	public static Item SHOP_PORTAL_ITEM;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(DirtWorldMod.MOD_ID, path);
	}

	public static void register() {
		ResourceKey<Item> tokenKey = ResourceKey.create(Registries.ITEM, id("dirt_token"));
		DIRT_TOKEN = Registry.register(BuiltInRegistries.ITEM, tokenKey,
				new Item(new Item.Properties().setId(tokenKey).stacksTo(64)));

		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("shop_portal"));
		SHOP_PORTAL = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new Block(BlockBehaviour.Properties.of()
						.strength(0.5F)
						.lightLevel(state -> 12)
						.sound(SoundType.AMETHYST)
						.setId(blockKey)));

		ResourceKey<Item> portalItemKey = ResourceKey.create(Registries.ITEM, id("shop_portal"));
		SHOP_PORTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, portalItemKey,
				new BlockItem(SHOP_PORTAL, new Item.Properties().setId(portalItemKey).useBlockDescriptionPrefix()));
	}

	private ModContent() {}
}
