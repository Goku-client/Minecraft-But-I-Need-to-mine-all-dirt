package com.dirtworld;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

/** Stores each player's token balance inside a player tag, so it survives restarts and death. */
public final class Bank {
	private static final String PREFIX = "dirtbank_";

	public static long get(Player p) {
		for (String t : p.getTags()) {
			if (t.startsWith(PREFIX)) {
				try {
					return Long.parseLong(t.substring(PREFIX.length()));
				} catch (NumberFormatException e) {
					return 0L;
				}
			}
		}
		return 0L;
	}

	public static void set(Player p, long value) {
		for (String t : new ArrayList<>(p.getTags())) {
			if (t.startsWith(PREFIX)) p.removeTag(t);
		}
		p.addTag(PREFIX + Math.max(0L, value));
	}

	public static void add(Player p, long amount) {
		set(p, get(p) + amount);
	}

	/** Moves every Dirt Token item in the inventory into the bank. Returns how many were moved. */
	public static long depositInventory(Player p) {
		Inventory inv = p.getInventory();
		long moved = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModContent.DIRT_TOKEN)) {
				moved += s.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		if (moved > 0) add(p, moved);
		return moved;
	}

	private Bank() {}
}
