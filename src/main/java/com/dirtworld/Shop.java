package com.dirtworld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.function.Function;

/** The 6 shop levels, from cheap to insane. Edit costs here. */
public final class Shop {

	public record Product(int level, long cost, Function<ServerLevel, ItemStack> maker, List<String> desc) {}

	private static Component name(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(color, ChatFormatting.BOLD);
	}

	public static final List<Product> PRODUCTS = List.of(
			new Product(1, 50,
					l -> gear(l, Items.NETHERITE_SHOVEL, name("Dirt Digger", ChatFormatting.GOLD),
							Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10, Enchantments.MENDING, 1),
					List.of("A shovel that melts dirt.", "Efficiency X, Unbreaking X, Mending")),
			new Product(2, 300,
					l -> DirtTools.make(Items.SLIME_BALL, 8, DirtTools.K_GRENADE, name("Dirt Grenade", ChatFormatting.GREEN)),
					List.of("x8. Right-click: erases dirt in a", "4-block radius where you look.")),
			new Product(3, 2500,
					l -> DirtTools.make(Items.NETHER_STAR, 1, DirtTools.K_NUKE, name("Dirt Nuke", ChatFormatting.RED)),
					List.of("Right-click: erases dirt in a", "14-block radius. Double tokens!")),
			new Product(4, 10000,
					l -> DirtTools.make(Items.BREEZE_ROD, 1, DirtTools.K_CHUNK, name("Chunk Eraser", ChatFormatting.LIGHT_PURPLE)),
					List.of("Right-click: deletes ALL dirt in the", "chunk you stand in, every height.")),
			new Product(5, 80000,
					l -> DirtTools.make(Items.HEART_OF_THE_SEA, 1, DirtTools.K_CANNON, name("Orbital Dirt Cannon", ChatFormatting.AQUA)),
					List.of("Reusable (5 min recharge).", "Wipes dirt in a 9x9 chunk area.")),
			new Product(6, 500000,
					l -> DirtTools.make(Items.ECHO_SHARD, 1, DirtTools.K_PLANET, name("PLANET ERASER", ChatFormatting.DARK_RED)),
					List.of("Wipes dirt from 25x25 chunks", "(about 400 blocks across)."))
	);

	@SuppressWarnings("unchecked")
	public static ItemStack gear(ServerLevel level, Item item, Component name, Object... pairs) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, name);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
			int lvl = (Integer) pairs[i + 1];
			Holder<Enchantment> holder = level.registryAccess()
					.lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(key);
			stack.enchant(holder, lvl);
		}
		return stack;
	}

	private Shop() {}
}
