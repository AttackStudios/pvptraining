package net.attackstudioyt.pvptraining.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.attackstudioyt.pvptraining.PVPTraining;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Builds the practice grounds the first time the world starts (and again whenever
 * the layout version changes), so the world is ready-made by the time you spawn.
 */
public final class ArenaBuilder {
	private static final int LAYOUT_VERSION = 1;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private ArenaBuilder() {}

	public static void ensureBuilt(MinecraftServer server) {
		Path marker = server.getSavePath(WorldSavePath.ROOT).resolve("pvptraining-layout.txt");
		try {
			if (Files.exists(marker) && Files.readString(marker).trim().equals(String.valueOf(LAYOUT_VERSION))) return;
		} catch (IOException ignored) {
			// rebuild
		}
		ServerWorld world = server.getOverworld();
		long start = System.currentTimeMillis();
		buildRound(world, Arena.HUB, Blocks.ORANGE_CONCRETE.getDefaultState(), 6, false);
		buildRound(world, Arena.MACE, Blocks.ORANGE_CONCRETE.getDefaultState(), 70, false);
		buildRound(world, Arena.SPEAR, Blocks.CYAN_CONCRETE.getDefaultState(), 40, false);
		buildRound(world, Arena.ELYTRA, Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(), 110, true);
		buildCrystalPit(world, Arena.CRYSTAL);
		buildTakeoffPad(world, Arena.ELYTRA);
		try {
			Files.writeString(marker, String.valueOf(LAYOUT_VERSION));
		} catch (IOException e) {
			PVPTraining.LOG.warn("Could not write the arena layout marker", e);
		}
		PVPTraining.LOG.info("Built the practice grounds in {} ms", System.currentTimeMillis() - start);
	}

	private static void set(ServerWorld world, int x, int y, int z, BlockState state) {
		world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
	}

	private static void buildRound(ServerWorld world, Arena a, BlockState accent, int wallHeight, boolean ceiling) {
		int r = a.radius;
		BlockState bedrock = Blocks.BEDROCK.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 1.5) continue;
				int x = a.x + dx;
				int z = a.z + dz;
				set(world, x, a.y - 2, z, bedrock);
				if (d > r + 0.5) {
					// rim: a low lit wall, then invisible barrier up to the ceiling
					set(world, x, a.y - 1, z, Blocks.POLISHED_BLACKSTONE.getDefaultState());
					set(world, x, a.y, z, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
					set(world, x, a.y + 1, z, ((dx + dz) & 3) == 0 ? Blocks.SEA_LANTERN.getDefaultState() : accent);
					for (int h = 2; h <= wallHeight; h++) set(world, x, a.y + h, z, barrier);
					continue;
				}
				set(world, x, a.y - 1, z, floorBlock(d, r, dx, dz, accent));
				if (ceiling) set(world, x, a.y + wallHeight, z, barrier);
			}
		}
	}

	private static BlockState floorBlock(double d, int r, int dx, int dz, BlockState accent) {
		if (d < 1.5) return Blocks.SEA_LANTERN.getDefaultState();
		if (d < 3.5) return accent;
		if (Math.abs(d - r * 0.5) < 0.6 || d > r - 1.2) return accent;
		if ((dx == 0 || dz == 0) && d > 3.5) return Blocks.POLISHED_DEEPSLATE.getDefaultState();
		int band = (int) (d / 4);
		return (band & 1) == 0 ? Blocks.DEEPSLATE_TILES.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
	}

	private static void buildCrystalPit(ServerWorld world, Arena a) {
		int r = a.radius;
		BlockState bedrock = Blocks.BEDROCK.getDefaultState();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int x = a.x + dx;
				int z = a.z + dz;
				boolean wall = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
				set(world, x, a.y - 2, z, bedrock);
				if (wall) {
					for (int h = -1; h <= 3; h++) set(world, x, a.y + h, z, bedrock);
					set(world, x, a.y + 4, z, ((dx + dz) & 3) == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.CRYING_OBSIDIAN.getDefaultState());
					for (int h = 5; h <= 40; h++) set(world, x, a.y + h, z, Blocks.BARRIER.getDefaultState());
				} else {
					set(world, x, a.y - 1, z, Blocks.OBSIDIAN.getDefaultState());
					set(world, x, a.y + 40, z, Blocks.BARRIER.getDefaultState());
				}
			}
		}
	}

	private static void buildTakeoffPad(ServerWorld world, Arena a) {
		BlockPos pad = BlockPos.ofFloored(a.playerSpawn()).down();
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				set(world, pad.getX() + dx, pad.getY(), pad.getZ() + dz, (dx == 0 && dz == 0) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
	}

	/** Puts an arena back to its clean state between rounds: player-placed blocks, crystals, drops. */
	public static void reset(ServerWorld world, Arena a) {
		int r = a.radius;
		int top = a == Arena.CRYSTAL ? 39 : 12;
		BlockState air = Blocks.AIR.getDefaultState();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (!a.square && dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) continue;
				for (int h = 0; h <= top; h++) {
					BlockPos pos = new BlockPos(a.x + dx, a.y + h, a.z + dz);
					if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, air, FLAGS);
				}
				if (a == Arena.CRYSTAL) {
					BlockPos floor = new BlockPos(a.x + dx, a.y - 1, a.z + dz);
					if (!world.getBlockState(floor).isOf(Blocks.OBSIDIAN)) world.setBlockState(floor, Blocks.OBSIDIAN.getDefaultState(), FLAGS);
				}
			}
		}
		clearLooseEntities(world, a);
	}

	public static void clearLooseEntities(ServerWorld world, Arena a) {
		Box box = new Box(a.x - a.radius - 2, a.y - 4, a.z - a.radius - 2, a.x + a.radius + 3, a.y + 130, a.z + a.radius + 3);
		List<Entity> loose = world.getOtherEntities(null, box, e -> !(e instanceof PlayerEntity));
		for (Entity e : loose) e.discard();
	}
}
