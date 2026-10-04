package com.dirtworld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** A 3-row chest GUI where clicking an item buys it with Dirt Tokens. */
public class ShopMenu extends ChestMenu {
	private static final int[] PRODUCT_SLOTS = { 10, 11, 12, 14, 15, 16 };
	private static final int BALANCE_SLOT = 13;

	private final SimpleContainer shop;
	private final ServerPlayer owner;

	private ShopMenu(int id, Inventory inv, SimpleContainer container, ServerPlayer owner) {
		super(MenuType.GENERIC_9x3, id, inv, container, 3);
		this.shop = container;
		this.owner = owner;
		refresh();
	}

	public static ShopMenu create(int id, Inventory inv, ServerPlayer sp) {
		long deposited = Bank.depositInventory(sp);
		if (deposited > 0) {
			sp.sendSystemMessage(Component.literal("Deposited " + String.format("%,d", deposited)
					+ " Dirt Tokens into your bank.").withStyle(ChatFormatting.GREEN));
		}
		return new ShopMenu(id, inv, new SimpleContainer(27), sp);
	}

	private static String fmt(long n) {
		return String.format("%,d", n);
	}

	private void refresh() {
		for (int i = 0; i < 27; i++) {
			ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
			pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
			shop.setItem(i, pane);
		}

		long balance = Bank.get(owner);

		ItemStack bal = new ItemStack(ModContent.DIRT_TOKEN);
		bal.set(DataComponents.CUSTOM_NAME, Component.literal("Your Dirt Tokens: " + fmt(balance))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		bal.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Mine dirt & grass to earn more.").withStyle(ChatFormatting.GRAY))));
		shop.setItem(BALANCE_SLOT, bal);

		ServerLevel level = (ServerLevel) owner.level();
		for (int i = 0; i < Shop.PRODUCTS.size(); i++) {
			Shop.Product p = Shop.PRODUCTS.get(i);
			ItemStack display = p.maker().apply(level);
			List<Component> lore = new ArrayList<>();
			lore.add(Component.literal("Shop Level " + p.level()).withStyle(ChatFormatting.DARK_GRAY));
			for (String line : p.desc()) {
				lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
			}
			lore.add(Component.literal(" "));
			boolean canAfford = balance >= p.cost();
			lore.add(Component.literal("Cost: " + fmt(p.cost()) + " tokens")
					.withStyle(canAfford ? ChatFormatting.GREEN : ChatFormatting.RED));
			lore.add(Component.literal(canAfford ? "Click to buy!" : "Not enough tokens")
					.withStyle(canAfford ? ChatFormatting.YELLOW : ChatFormatting.DARK_RED));
			display.set(DataComponents.LORE, new ItemLore(lore));
			shop.setItem(PRODUCT_SLOTS[i], display);
		}
	}

	private void buy(int productIndex) {
		Shop.Product p = Shop.PRODUCTS.get(productIndex);
		long balance = Bank.get(owner);
		if (balance < p.cost()) {
			owner.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
			owner.sendSystemMessage(Component.literal("Not enough Dirt Tokens! Need " + fmt(p.cost())
					+ ", you have " + fmt(balance) + ".").withStyle(ChatFormatting.RED));
			return;
		}
		Bank.set(owner, balance - p.cost());
		ItemStack bought = p.maker().apply((ServerLevel) owner.level());
		owner.getInventory().placeItemBackInInventory(bought);
		owner.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		owner.sendSystemMessage(Component.literal("Bought ").withStyle(ChatFormatting.GREEN)
				.append(bought.getHoverName()).append(Component.literal("!")));
		refresh();
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL
				|| clickType == ClickType.QUICK_MOVE) {
			return; // never let items move in or out of the shop
		}
		if (slotId >= 0 && slotId < 27) {
			if (clickType == ClickType.PICKUP) {
				for (int i = 0; i < PRODUCT_SLOTS.length; i++) {
					if (PRODUCT_SLOTS[i] == slotId) {
						buy(i);
						break;
					}
				}
			}
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}
}
