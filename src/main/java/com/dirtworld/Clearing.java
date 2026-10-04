package com.dirtworld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Everything that removes dirt from the world. */
public final class Clearing {

	public static boolean isDirt(BlockState s) {
		return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT)
				|| s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MYCELIUM)
				|| s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND);
	}

	private static boolean isPlant(BlockState s) {
		return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SHORT_GRASS)
				|| s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
				|| s.is(Blocks.DEAD_BUSH);
	}

	private static void removePlantsAbove(ServerLevel level, BlockPos below) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= 2; i++) {
			q.set(below.getX(), below.getY() + i, below.getZ());
			if (isPlant(level.getBlockState(q))) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
			} else {
				break;
			}
		}
	}

	/** Removes dirt in a sphere. Returns the number of blocks removed. */
	public static int sphere(ServerLevel level, BlockPos center, int r) {
		int minY = level.dimensionType().minY();
		int maxY = minY + level.dimensionType().height() - 1;
		int count = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r) continue;
					int y = center.getY() + dy;
					if (y < minY || y > maxY) continue;
					p.set(center.getX() + dx, y, center.getZ() + dz);
					if (isDirt(level.getBlockState(p))) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
						removePlantsAbove(level, p);
						count++;
					}
				}
			}
		}
		return count;
	}

	/** Removes all dirt in one whole chunk column (all heights). Returns blocks removed. */
	public static int chunk(ServerLevel level, int cx, int cz) {
		LevelChunk chunk = level.getChunk(cx, cz);
		LevelChunkSection[] sections = chunk.getSections();
		int minY = level.dimensionType().minY();
		int count = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection sec = sections[i];
			if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(Clearing::isDirt)) continue;
			int baseY = minY + i * 16;
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						if (isDirt(sec.getBlockState(lx, ly, lz))) {
							p.set(cx * 16 + lx, baseY + ly, cz * 16 + lz);
							level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
							removePlantsAbove(level, p);
							count++;
						}
					}
				}
			}
		}
		return count;
	}

	// ---------- Long-running area jobs (processed a few chunks per tick) ----------
	private static final class Job {
		final ServerLevel level;
		final UUID owner;
		final ArrayDeque<int[]> queue = new ArrayDeque<>();
		final int perTick;
		final int mult;
		final int totalChunks;
		int done = 0;
		long gained = 0;

		Job(ServerLevel level, UUID owner, int perTick, int mult, int totalChunks) {
			this.level = level;
			this.owner = owner;
			this.perTick = perTick;
			this.mult = mult;
			this.totalChunks = totalChunks;
		}
	}

	private static final List<Job> JOBS = new ArrayList<>();

	public static void startArea(ServerLevel level, ServerPlayer owner, int centerCx, int centerCz,
			int radiusChunks, int perTick, int mult) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
				list.add(new int[] { centerCx + dx, centerCz + dz });
			}
		}
		list.sort(Comparator.comparingInt(a -> {
			int ax = a[0] - centerCx, az = a[1] - centerCz;
			return ax * ax + az * az;
		}));
		Job job = new Job(level, owner.getUUID(), perTick, mult, list.size());
		job.queue.addAll(list);
		JOBS.add(job);
	}

	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) return;
		Iterator<Job> it = JOBS.iterator();
		while (it.hasNext()) {
			Job j = it.next();
			long gainedNow = 0;
			for (int i = 0; i < j.perTick && !j.queue.isEmpty(); i++) {
				int[] c = j.queue.poll();
				gainedNow += (long) chunk(j.level, c[0], c[1]) * j.mult;
				j.done++;
			}
			j.gained += gainedNow;
			ServerPlayer p = server.getPlayerList().getPlayer(j.owner);
			if (p != null) {
				if (gainedNow > 0) Bank.add(p, gainedNow);
				boolean finished = j.queue.isEmpty();
				if (finished || j.level.getGameTime() % 10 == 0) {
					String text = (finished ? "Done! " : "Erasing dirt... ") + j.done + "/" + j.totalChunks
							+ " chunks  |  +" + String.format("%,d", j.gained) + " tokens";
					p.displayClientMessage(Component.literal(text)
							.withStyle(finished ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
				}
			}
			if (j.queue.isEmpty()) it.remove();
		}
	}

	private Clearing() {}
}
