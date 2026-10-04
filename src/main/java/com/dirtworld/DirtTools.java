package com.dirtworld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The single-use / reusable dirt-erasing items sold in the shop. */
public final class DirtTools {
	public static final String K_GRENADE = "dirtworld_grenade";
	public static final String K_NUKE = "dirtworld_nuke";
	public static final String K_CHUNK = "dirtworld_chunk";
	public static final String K_CANNON = "dirtworld_cannon";
	public static final String K_PLANET = "dirtworld_planet";
	private static final String[] KEYS = { K_GRENADE, K_NUKE, K_CHUNK, K_CANNON, K_PLANET };

	// Bonus multipliers: tokens earned per dirt block removed by each tool.
	private static final int MULT_GRENADE = 1;
	private static final int MULT_NUKE = 2;
	private static final int MULT_CHUNK = 8;
	private static final int MULT_CANNON = 4;
	private static final int MULT_PLANET = 5;

	// Orbital Cannon recharge (ticks) and area sizes (in chunks of radius).
	private static final long CANNON_COOLDOWN = 6000L; // 5 minutes
	private static final int CANNON_RADIUS_CHUNKS = 4;
	private static final int PLANET_RADIUS_CHUNKS = 12;

	private static final Map<UUID, Long> CANNON_READY = new HashMap<>();

	public static ItemStack make(Item item, int count, String key, Component name) {
		ItemStack s = new ItemStack(item, count);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(key, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, name);
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}

	public static String toolKey(ItemStack stack) {
		if (stack.isEmpty()) return null;
		CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
		if (cd == null) return null;
		for (String k : KEYS) {
			if (cd.copyTag().contains(k)) return k;
		}
		return null;
	}

	private static BlockPos target(ServerPlayer sp, double range) {
		HitResult hit = sp.pick(range, 1.0F, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			return ((BlockHitResult) hit).getBlockPos();
		}
		return sp.blockPosition();
	}

	private static void boom(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
	}

	private static void credit(ServerPlayer sp, String toolName, int blocks, int mult) {
		long tokens = (long) blocks * mult;
		Bank.add(sp, tokens);
		sp.displayClientMessage(Component.literal(toolName + "!  " + String.format("%,d", blocks)
				+ " dirt erased  |  +" + String.format("%,d", tokens) + " tokens")
				.withStyle(ChatFormatting.GOLD), true);
	}

	public static InteractionResult use(ServerLevel level, ServerPlayer sp, ItemStack stack, String key) {
		long now = level.getGameTime();
		int cx = sp.blockPosition().getX() >> 4;
		int cz = sp.blockPosition().getZ() >> 4;
		switch (key) {
			case K_GRENADE -> {
				BlockPos t = target(sp, 40);
				int n = Clearing.sphere(level, t, 4);
				boom(level, t.getX(), t.getY(), t.getZ());
				credit(sp, "Dirt Grenade", n, MULT_GRENADE);
				stack.shrink(1);
			}
			case K_NUKE -> {
				BlockPos t = target(sp, 60);
				int n = Clearing.sphere(level, t, 14);
				boom(level, t.getX(), t.getY(), t.getZ());
				credit(sp, "DIRT NUKE", n, MULT_NUKE);
				stack.shrink(1);
			}
			case K_CHUNK -> {
				int n = Clearing.chunk(level, cx, cz);
				boom(level, sp.getX(), sp.getY(), sp.getZ());
				credit(sp, "Chunk Eraser", n, MULT_CHUNK);
				stack.shrink(1);
			}
			case K_CANNON -> {
				long ready = CANNON_READY.getOrDefault(sp.getUUID(), 0L);
				if (now < ready) {
					sp.displayClientMessage(Component.literal("Cannon recharging... " + ((ready - now) / 20) + "s")
							.withStyle(ChatFormatting.RED), true);
					return InteractionResult.SUCCESS;
				}
				CANNON_READY.put(sp.getUUID(), now + CANNON_COOLDOWN);
				Clearing.startArea(level, sp, cx, cz, CANNON_RADIUS_CHUNKS, 3, MULT_CANNON);
				boom(level, sp.getX(), sp.getY(), sp.getZ());
				sp.sendSystemMessage(Component.literal("Orbital Dirt Cannon FIRED!")
						.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
			}
			case K_PLANET -> {
				stack.shrink(1);
				Clearing.startArea(level, sp, cx, cz, PLANET_RADIUS_CHUNKS, 1, MULT_PLANET);
				boom(level, sp.getX(), sp.getY(), sp.getZ());
				level.getServer().getPlayerList().broadcastSystemMessage(
						Component.literal("PLANET ERASER activated by ")
								.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
								.append(sp.getDisplayName()), false);
			}
			default -> { }
		}
		return InteractionResult.SUCCESS;
	}

	private DirtTools() {}
}
