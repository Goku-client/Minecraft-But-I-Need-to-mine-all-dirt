package com.dirtworld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The shop is a sealed, lit room built far away in the End dimension (empty void out there).
 * It is built the first time someone enters.
 */
public final class ShopWorld {
	public static final int CX = 500;
	public static final int CZ = 500;
	public static final int FLOOR_Y = 150;
	public static final int HALF = 8;
	private static final String KEEPER_NAME = "Dirt Merchant";
	private static final String RETURN_PREFIX = "dirtret_";

	public static boolean inside(ServerLevel level, BlockPos pos) {
		return level.dimension().equals(Level.END)
				&& pos.getX() >= CX - HALF && pos.getX() <= CX + HALF
				&& pos.getZ() >= CZ - HALF && pos.getZ() <= CZ + HALF
				&& pos.getY() >= FLOOR_Y && pos.getY() <= FLOOR_Y + 8;
	}

	public static boolean isKeeper(Entity e) {
		if (!(e instanceof Villager) || !e.hasCustomName()) return false;
		return KEEPER_NAME.equals(e.getCustomName().getString());
	}

	// ---------- teleporting ----------
	public static void enter(ServerLevel from, ServerPlayer sp, BlockPos portalPos) {
		MinecraftServer server = from.getServer();
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) {
			sp.sendSystemMessage(Component.literal("The shop dimension is unavailable.").withStyle(ChatFormatting.RED));
			return;
		}
		rememberReturn(sp, from, portalPos);
		ensureBuilt(end);
		sp.teleport(new TeleportTransition(end, new Vec3(CX + 0.5, FLOOR_Y + 1.0, CZ + 5.5),
				Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING));
		sp.sendSystemMessage(Component.literal("Welcome to the Dirt Shop! Talk to the Dirt Merchant.")
				.withStyle(ChatFormatting.GOLD));
	}

	public static void exit(ServerLevel from, ServerPlayer sp) {
		MinecraftServer server = from.getServer();
		String tag = null;
		for (String t : sp.getTags()) {
			if (t.startsWith(RETURN_PREFIX)) { tag = t; break; }
		}
		ServerLevel dest = server.overworld();
		Vec3 pos = null;
		if (tag != null) {
			try {
				String[] parts = tag.split("_");
				char d = parts[1].charAt(0);
				int x = Integer.parseInt(parts[2]);
				int y = Integer.parseInt(parts[3]);
				int z = Integer.parseInt(parts[4]);
				ServerLevel lvl = server.getLevel(d == 'n' ? Level.NETHER : d == 'e' ? Level.END : Level.OVERWORLD);
				if (lvl != null) {
					dest = lvl;
					pos = new Vec3(x + 0.5, y + 1.0, z + 0.5);
				}
			} catch (RuntimeException ignored) {
				pos = null;
			}
		}
		if (pos == null) {
			dest = server.overworld();
			int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
			pos = new Vec3(0.5, y + 1.0, 0.5);
		}
		sp.teleport(new TeleportTransition(dest, pos, Vec3.ZERO, sp.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
	}

	private static void rememberReturn(ServerPlayer sp, ServerLevel from, BlockPos pos) {
		for (String t : new java.util.ArrayList<>(sp.getTags())) {
			if (t.startsWith(RETURN_PREFIX)) sp.removeTag(t);
		}
		char d = from.dimension().equals(Level.NETHER) ? 'n' : from.dimension().equals(Level.END) ? 'e' : 'o';
		sp.addTag(RETURN_PREFIX + d + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ());
	}

	// ---------- building the room ----------
	public static void ensureBuilt(ServerLevel end) {
		BlockPos marker = new BlockPos(CX, FLOOR_Y, CZ);
		if (!end.getBlockState(marker).is(Blocks.SEA_LANTERN)) {
			build(end);
		}
		BlockPos portal = new BlockPos(CX + 5, FLOOR_Y + 1, CZ + 5);
		if (!end.getBlockState(portal).is(ModContent.SHOP_PORTAL)) {
			end.setBlock(portal, ModContent.SHOP_PORTAL.defaultBlockState(), 3);
		}
		ensureKeeper(end);
	}

	private static void build(ServerLevel end) {
		BlockState brick = Blocks.MUD_BRICKS.defaultBlockState();
		BlockState packed = Blocks.PACKED_MUD.defaultBlockState();
		BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		int top = FLOOR_Y + 7;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = CX - HALF; x <= CX + HALF; x++) {
			for (int z = CZ - HALF; z <= CZ + HALF; z++) {
				boolean wall = x == CX - HALF || x == CX + HALF || z == CZ - HALF || z == CZ + HALF;
				for (int y = FLOOR_Y; y <= top; y++) {
					p.set(x, y, z);
					BlockState s;
					if (y == FLOOR_Y) {
						s = ((x + z) % 2 == 0) ? brick : packed;
					} else if (y == top) {
						s = (Math.floorMod(x + z, 4) == 0) ? lantern : brick;
					} else if (wall) {
						s = brick;
					} else {
						s = air;
					}
					end.setBlock(p, s, 2);
				}
			}
		}
		// gold pillars in the inner corners
		int[][] corners = { { -7, -7 }, { -7, 7 }, { 7, -7 }, { 7, 7 } };
		for (int[] c : corners) {
			for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 6; y++) {
				end.setBlock(p.set(CX + c[0], y, CZ + c[1]), gold, 2);
			}
		}
		// glowing podium marker in the middle of the floor
		end.setBlock(new BlockPos(CX, FLOOR_Y, CZ), lantern, 2);
	}

	private static void ensureKeeper(ServerLevel end) {
		AABB box = new AABB(CX - HALF, FLOOR_Y, CZ - HALF, CX + HALF + 1, FLOOR_Y + 8, CZ + HALF + 1);
		for (Villager v : end.getEntitiesOfClass(Villager.class, box)) {
			if (isKeeper(v)) return;
		}
		Villager v = EntityType.VILLAGER.create(end, EntitySpawnReason.EVENT);
		if (v == null) return;
		v.setPos(CX + 0.5, FLOOR_Y + 1.0, CZ - 2.5);
		v.setNoAi(true);
		v.setInvulnerable(true);
		v.setPersistenceRequired();
		v.setCustomName(Component.literal(KEEPER_NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		v.setCustomNameVisible(true);
		end.addFreshEntity(v);
	}

	/** Keeps the room free of monsters (endermen love the End). */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) return;
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return;
		AABB box = new AABB(CX - HALF, FLOOR_Y, CZ - HALF, CX + HALF + 1, FLOOR_Y + 8, CZ + HALF + 1);
		for (Monster m : end.getEntitiesOfClass(Monster.class, box)) {
			m.discard();
		}
	}

	private ShopWorld() {}
}
