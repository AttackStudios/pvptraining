package net.attackstudioyt.pvptraining.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** One training ground in the practice world. Round arenas use radius, the crystal pit is square. */
public enum Arena {
	HUB("Hub", 0, 100, 0, 10, false),
	MACE("Mace Arena", 300, 100, 0, 28, false),
	SPEAR("Spear Arena", 600, 100, 0, 32, false),
	ELYTRA("Elytra Arena", 900, 100, 0, 40, false),
	CRYSTAL("Crystal Pit", 1200, 100, 0, 20, true);

	public final String title;
	public final int x;
	/** Y of the air block you stand in; the floor block is one below. */
	public final int y;
	public final int z;
	public final int radius;
	public final boolean square;

	Arena(String title, int x, int y, int z, int radius, boolean square) {
		this.title = title;
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
		this.square = square;
	}

	public Vec3d center() {
		return new Vec3d(x + 0.5, y, z + 0.5);
	}

	public BlockPos centerBlock() {
		return new BlockPos(x, y, z);
	}

	/** Where the trainee stands at the start of a round, facing +X toward the bot. */
	public Vec3d playerSpawn() {
		return new Vec3d(x + 0.5 - radius * 0.35, y, z + 0.5);
	}

	public Vec3d botSpawn() {
		return new Vec3d(x + 0.5 + radius * 0.35, y, z + 0.5);
	}

	public boolean contains(Vec3d pos) {
		return distanceToEdge(pos) >= 0;
	}

	/** Blocks of room left before the wall; negative when outside. */
	public double distanceToEdge(Vec3d pos) {
		double dx = pos.x - (x + 0.5);
		double dz = pos.z - (z + 0.5);
		if (square) return radius - Math.max(Math.abs(dx), Math.abs(dz));
		return radius - Math.sqrt(dx * dx + dz * dz);
	}

	public Vec3d randomPoint(net.minecraft.util.math.random.Random random, double margin) {
		double r = Math.max(2, radius - margin);
		if (square) return new Vec3d(x + 0.5 + (random.nextDouble() * 2 - 1) * r, y, z + 0.5 + (random.nextDouble() * 2 - 1) * r);
		double angle = random.nextDouble() * Math.PI * 2;
		double d = Math.sqrt(random.nextDouble()) * r;
		return new Vec3d(x + 0.5 + Math.cos(angle) * d, y, z + 0.5 + Math.sin(angle) * d);
	}
}
