package net.attackstudioyt.pvptraining.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.attackstudioyt.pvptraining.Catalog;
import net.attackstudioyt.pvptraining.Kits;
import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.attackstudioyt.pvptraining.bot.CrystalBrain;
import net.attackstudioyt.pvptraining.bot.DummyBrain;
import net.attackstudioyt.pvptraining.bot.Skill;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** All twelve drills. Each one is a small rule set on top of the same session plumbing. */
public class DrillSession extends Session {
	private final Catalog.Drill drill;
	private final String id;

	private double score;
	private int attemptsLeft;
	private int timeLeft;

	private BotPlayer dummy;
	private DummyBrain dummyBrain;
	private CrystalBrain attacker;

	private int lastLungeTick = -9999;
	private int lastGlideTick = -9999;
	private int lastSmashTick = -9999;
	private int lastCrossbowTick = -9999;
	private int chain;
	private double bestSingle;
	private int hits;
	private int tries;

	// airtime tracking, so a whiffed dive costs an attempt
	private boolean airborne;
	private boolean bigFall;
	private boolean smashedThisAir;
	private boolean glidedThisAir;

	// ely_climb
	private boolean climbing;
	private int climbStart;
	private int airStart = -1;
	private int groundTicks;
	private boolean ringBuilt;

	// spear_lunge
	private Vec3d ring;

	// crystal_speed
	private final Map<Integer, Vec3d> crystals = new HashMap<>();

	public DrillSession(ServerPlayerEntity player, String modeId, Catalog.Drill drill) {
		super(player, modeId);
		this.drill = drill;
		this.id = drill.id;
	}

	@Override
	public String name() {
		return drill.name;
	}

	@Override
	public void start() {
		Kits.give(player, modeId);
		toSpawn();
		attemptsLeft = drill.attempts;
		timeLeft = drill.seconds * 20;
		switch (id) {
			case "mace_smash", "mace_chain", "ely_dive", "crystal_speed" -> dummy(DummyBrain.Style.STILL, false);
			case "mace_chase", "spear_smash", "ely_full", "sword_combo" -> dummy(DummyBrain.Style.WANDER, false);
			case "sword_wtap", "sword_crit", "cart_speed", "cart_damage", "xbow_setup", "xbow_damage" -> dummy(DummyBrain.Style.STILL, false);
			case "cart_moving", "xbow_moving" -> dummy(DummyBrain.Style.WANDER, false);
			case "ely_climb" -> buildRing(true); // up before the countdown, so the target is visible while you wait
			case "crystal_pops" -> dummy(DummyBrain.Style.STILL, true);
			case "crystal_survive" -> {
				BotPlayer bot = spawnBot("Bomber");
				attacker = new CrystalBrain(bot, arena, new Skill(6), player);
				bot.brain = attacker;
			}
			default -> { }
		}
		title(Text.literal(drill.name).formatted(Formatting.GOLD, Formatting.BOLD), Text.literal(shortGoal()), 50);
		beginCountdown();
	}

	private void dummy(DummyBrain.Style style, boolean totem) {
		dummy = spawnBot("Dummy");
		dummyBrain = new DummyBrain(dummy, arena, player, style, totem);
		dummy.brain = dummyBrain;
	}

	private String shortGoal() {
		return switch (id) {
			case "mace_smash" -> "Wind charge up, smash the dummy";
			case "mace_chase" -> "Smash the moving dummy";
			case "mace_chain" -> "Chain smashes without stopping";
			case "spear_lunge" -> "Lunge through the rings";
			case "spear_swap" -> "Swap and lunge as fast as you can";
			case "spear_smash" -> "Lunge in, launch, smash";
			case "ely_climb" -> "Wings on, rocket up through the ring";
			case "ely_dive" -> "Dive, wings off, smash";
			case "ely_full" -> "Full climb and dive on a moving target";
			case "crystal_speed" -> "Place and pop beside the dummy";
			case "crystal_pops" -> "Pop its totems";
			case "crystal_survive" -> "Re-totem and stay alive";
			case "cart_speed" -> "Rail, cart, Flame arrow. Again.";
			case "cart_damage" -> "Full draw for the biggest blast";
			case "cart_moving" -> "Cart the moving dummy";
			case "xbow_setup" -> "Rail, cart, fire, then shoot through the fire";
			case "xbow_damage" -> "Crossbow carts hit hardest";
			case "xbow_moving" -> "Crossbow cart the moving dummy";
			case "sword_combo" -> "Keep the hits coming without a gap";
			case "sword_wtap" -> "Reset your sprint between every hit";
			case "sword_crit" -> "Jump, and swing on the way down";
			default -> "";
		};
	}

	/* -------------------------------------------------------------------- live */

	@Override
	protected void live() {
		if (player.isGliding()) lastGlideTick = ticks;
		if (player.getMainHandStack().isOf(Items.CROSSBOW)) lastCrossbowTick = ticks;
		trackAirtime();
		switch (id) {
			case "ely_climb" -> tickClimb();
			case "spear_lunge" -> tickRings();
			case "crystal_speed" -> tickCrystals();
			case "crystal_pops" -> score = dummyBrain.pops;
			case "crystal_survive" -> score = Math.min(drill.seconds, (drill.seconds * 20 - timeLeft) / 20);
			default -> { }
		}
		boolean sword = modeId.equals("sword");
		if (chain > 0 && ticks - lastSmashTick > (sword ? COMBO_GAP : 80)) {
			if (!sword || chain >= 3) say((sword ? "Combo" : "Chain") + " dropped at " + chain, "bad");
			chain = 0;
		}

		if (drill.seconds > 0) {
			timeLeft--;
			setBar(drill.name + "  ·  " + fmt(score) + " " + drill.unit + "  ·  " + (timeLeft / 20 + 1) + "s", timeLeft / (drill.seconds * 20.0F), BossBar.Color.YELLOW);
			if (timeLeft <= 0) finish();
		} else {
			String shown = id.equals("ely_climb") ? (score > 0 ? fmt(score) + "s best" : "no time yet") + "  ·  height " + Math.max(0, (int) (player.getY() - arena.y)) + "/" + CLIMB_HEIGHT : fmt(score) + " " + drill.unit;
			setBar(drill.name + "  ·  " + shown + "  ·  " + attemptsLeft + " left", attemptsLeft / (float) drill.attempts, BossBar.Color.YELLOW);
			if (attemptsLeft <= 0) finish();
		}
	}

	private void trackAirtime() {
		if (!usesAttempts() || id.equals("ely_climb")) return;
		if (!player.isOnGround()) {
			airborne = true;
			if (player.fallDistance > 3.5 || player.isGliding()) bigFall = true;
			if (player.isGliding()) glidedThisAir = true;
			return;
		}
		if (!airborne) return;
		boolean counts = id.equals("ely_dive") ? glidedThisAir : bigFall;
		if (counts && !smashedThisAir) {
			attemptsLeft--;
			tries++;
			say("Missed. " + attemptsLeft + " attempts left", "bad");
		}
		airborne = false;
		bigFall = false;
		smashedThisAir = false;
		glidedThisAir = false;
	}

	private boolean usesAttempts() {
		return drill.attempts > 0;
	}

	@Override
	public void onPlayerHit(LivingEntity victim, DamageSource source, float taken) {
		if (countdown > 0 || victim != dummy) return;
		if (modeId.equals("sword")) {
			swordHit(source, taken);
			return;
		}
		if (modeId.equals("cart") || modeId.equals("xbow")) {
			cartHit(source, taken);
			return;
		}
		boolean smash = player.getMainHandStack().isOf(Items.MACE) && player.fallDistance > 1.5F && !player.isGliding();
		if (!smash) return;
		int fell = (int) Math.round(player.fallDistance);
		switch (id) {
			case "mace_smash" -> scoreSmash(taken, fell, true);
			case "ely_dive" -> {
				if (ticks - lastGlideTick > 100) {
					say("That was not an elytra dive. Glide in, then take the wings off.", "bad");
					return;
				}
				scoreSmash(taken, fell, true);
			}
			case "mace_chase" -> countSmash(taken, fell);
			case "spear_smash" -> {
				if (ticks - lastLungeTick > 120) say("Smash landed, but lunge in first for it to count", "");
				else countSmash(taken, fell);
			}
			case "ely_full" -> {
				if (ticks - lastGlideTick > 100) say("Smash landed, but it has to come from an elytra dive", "");
				else countSmash(taken, fell);
			}
			case "mace_chain" -> {
				chain = ticks - lastSmashTick <= 80 ? chain + 1 : 1;
				lastSmashTick = ticks;
				hits++;
				if (chain > score) score = chain;
				say("Chain x" + chain + " (" + fmt(taken) + " damage)", "good");
				ding();
			}
			default -> { }
		}
	}

	/**
	 * Sword drills. Only charged hits count (a spammed swing does a fraction of the damage), and
	 * each drill looks at what the trainee was doing at the moment the hit landed:
	 * sprinting = a sprint-reset hit, falling and not sprinting = a critical.
	 */
	private void swordHit(DamageSource source, float taken) {
		if (source.getSource() != player || taken < 5.0F) return;
		hits++;
		bestSingle = Math.max(bestSingle, taken);
		switch (id) {
			case "sword_combo" -> {
				chain = ticks - lastSmashTick <= COMBO_GAP ? chain + 1 : 1;
				lastSmashTick = ticks;
				if (chain > score) score = chain;
				if (chain >= 3) say("Combo x" + chain, "good");
			}
			case "sword_wtap" -> {
				if (player.isSprinting()) {
					score++;
					if (score % 5 == 0) say(fmt(score) + " sprint hits", "good");
				}
			}
			case "sword_crit" -> {
				boolean crit = !player.isOnGround() && player.fallDistance > 0 && !player.isSprinting() && !player.isClimbing() && !player.isTouchingWater();
				if (crit) {
					score++;
					ding();
				}
			}
			default -> { }
		}
	}

	/**
	 * Cart and X-Bow drills score minecart blasts that reach the dummy. For X-Bow the blast only
	 * counts if the crossbow was in hand moments before: a Flame bow cart is the other gamemode.
	 */
	private void cartHit(DamageSource source, float taken) {
		if (!source.isIn(DamageTypeTags.IS_EXPLOSION) || taken < 4.0F) return;
		if (modeId.equals("xbow") && ticks - lastCrossbowTick > 60) {
			say("That cart was not set off with the crossbow. Light a fire and shoot through it.", "");
			return;
		}
		hits++;
		bestSingle = Math.max(bestSingle, taken);
		if (usesAttempts()) {
			score += taken;
			attemptsLeft--;
			tries++;
			say("Blast for " + fmt(taken) + " damage", "good");
		} else {
			score++;
			say("Cart " + fmt(score) + ": " + fmt(taken) + " damage", "good");
		}
		ding();
	}

	/** Longest gap between two hits of one combo: a full sword cooldown plus a little slack. */
	private static final int COMBO_GAP = 18;

	private void scoreSmash(float taken, int fell, boolean usesAttempt) {
		score += taken;
		hits++;
		tries++;
		smashedThisAir = true;
		bestSingle = Math.max(bestSingle, taken);
		if (usesAttempt) attemptsLeft--;
		say("Smash for " + fmt(taken) + " damage from " + fell + " blocks", "good");
		ding();
	}

	private void countSmash(float taken, int fell) {
		score++;
		hits++;
		bestSingle = Math.max(bestSingle, taken);
		say("Smash " + fmt(score) + ": " + fmt(taken) + " damage from " + fell + " blocks", "good");
		ding();
	}

	private void ding() {
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.3F);
	}

	@Override
	public void onLunge() {
		if (countdown > 0) return;
		lastLungeTick = ticks;
		if (id.equals("spear_swap")) {
			score++;
			if (score % 5 == 0) say(fmt(score) + " lunges", "good");
		}
	}

	@Override
	public void onPlayerDown(DamageSource source) {
		if (id.equals("crystal_survive")) {
			say("You went down after " + fmt(score) + " seconds", "bad");
			finish();
			return;
		}
		say("You went down. Back to the start.", "bad");
		Kits.give(player, modeId);
		toSpawn();
	}

	@Override
	public void onBotDown(BotPlayer bot, DamageSource source) {
		// drill targets never stay down
		bot.setHealth(bot.getMaxHealth());
	}

	/* -------------------------------------------------------------- ely_climb */

	private static final int CLIMB_HEIGHT = 60;
	private static final int RING_RADIUS = 7;

	/** A ring of sea lanterns high above the pad: something solid to aim for, visible from the ground. */
	private void buildRing(boolean place) {
		BlockPos pad = BlockPos.ofFloored(arena.playerSpawn());
		int y = arena.y + CLIMB_HEIGHT;
		for (int dx = -RING_RADIUS - 1; dx <= RING_RADIUS + 1; dx++) {
			for (int dz = -RING_RADIUS - 1; dz <= RING_RADIUS + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < RING_RADIUS - 0.5 || d > RING_RADIUS + 0.9) continue;
				BlockPos pos = new BlockPos(pad.getX() + dx, y, pad.getZ() + dz);
				world.setBlockState(pos, place ? (((dx + dz) & 1) == 0 ? Blocks.SEA_LANTERN : Blocks.LIGHT_BLUE_STAINED_GLASS).getDefaultState() : Blocks.AIR.getDefaultState());
			}
		}
		ringBuilt = place;
	}

	private void tickClimb() {
		double goal = arena.y + CLIMB_HEIGHT;
		if (!ringBuilt) buildRing(true);
		// a guide beam from the pad up to the ring, so "up there" is never in doubt
		if (ticks % 5 == 0) {
			Vec3d c = arena.playerSpawn();
			for (int h = 4; h < CLIMB_HEIGHT; h += 4) world.spawnParticles(player, ParticleTypes.END_ROD, true, true, c.x, arena.y + h, c.z, 1, 0.05, 0.4, 0.05, 0);
		}
		if (!player.isOnGround() && airStart < 0) airStart = ticks;
		if (player.isOnGround() && !climbing) {
			airStart = -1;
			groundTicks++;
			if (groundTicks % 160 == 0) say("Elytra on (slot 6), jump, press jump again in the air, then fire a rocket straight up", "");
		}
		if (!climbing) {
			// The clock only starts once you are actually flying; a plain jump costs nothing.
			if (player.isGliding()) {
				climbing = true;
				climbStart = airStart < 0 ? ticks : airStart;
				groundTicks = 0;
			}
			return;
		}
		if (player.getY() >= goal) {
			double seconds = (ticks - climbStart) / 20.0;
			if (score <= 0 || seconds < score) score = seconds;
			hits++;
			say("Through the ring in " + fmt(seconds) + "s", "good");
			ding();
			nextClimb();
		} else if (player.isOnGround() || ticks - climbStart > 400) {
			say("Landed before the ring. It is " + CLIMB_HEIGHT + " blocks up: keep the rockets coming", "bad");
			nextClimb();
		}
	}

	@Override
	public void end() {
		if (ringBuilt) buildRing(false);
		super.end();
	}

	private void nextClimb() {
		climbing = false;
		airStart = -1;
		attemptsLeft--;
		tries++;
		if (attemptsLeft > 0) {
			Kits.give(player, modeId);
			toSpawn();
		}
	}

	/* ------------------------------------------------------------ spear_lunge */

	private void tickRings() {
		if (ring == null) nextRing();
		if (ticks % 3 == 0) {
			for (int i = 0; i < 16; i++) {
				double a = i / 16.0 * Math.PI * 2;
				world.spawnParticles(player, ParticleTypes.END_ROD, true, true, ring.x + Math.cos(a) * 1.6, ring.y + 1.0, ring.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
			}
			world.spawnParticles(player, ParticleTypes.GLOW, true, true, ring.x, ring.y + 1.0, ring.z, 2, 0.1, 0.8, 0.1, 0);
		}
		if (player.getEntityPos().distanceTo(ring) < 2.2) {
			if (ticks - lastLungeTick <= 30) {
				score++;
				ding();
				nextRing();
			} else if (ticks % 20 == 0) say("Lunge through it, walking does not count", "");
		}
	}

	private void nextRing() {
		for (int i = 0; i < 20; i++) {
			Vec3d p = arena.randomPoint(world.getRandom(), 5);
			double d = p.distanceTo(player.getEntityPos());
			if (d >= 9 && d <= 17) {
				ring = p;
				return;
			}
		}
		ring = arena.randomPoint(world.getRandom(), 5);
	}

	/* ---------------------------------------------------------- crystal_speed */

	private void tickCrystals() {
		Box zone = dummy.getBoundingBox().expand(7);
		for (EndCrystalEntity c : world.getEntitiesByClass(EndCrystalEntity.class, zone, e -> true)) crystals.putIfAbsent(c.getId(), c.getEntityPos());
		Iterator<Map.Entry<Integer, Vec3d>> it = crystals.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Vec3d> e = it.next();
			if (world.getEntityById(e.getKey()) != null) continue;
			it.remove();
			score++;
		}
	}

	/* ------------------------------------------------------------------ result */

	@Override
	protected Map<String, String> stats() {
		String main = id.equals("ely_climb") ? (score > 0 ? fmt(score) + "s" : "none") : fmt(score);
		String second = drill.seconds > 0 ? (Math.max(0, timeLeft) / 20) + "s" : String.valueOf(Math.max(0, attemptsLeft));
		return switch (id) {
			case "mace_smash", "ely_dive" -> map("Total damage", main, "Attempts left", second, "Best smash", fmt(bestSingle), "Landed", hits + "/" + tries);
			case "mace_chain" -> map("Best chain", main, "Time left", second, "Current chain", String.valueOf(chain), "Smashes", String.valueOf(hits));
			case "ely_climb" -> map("Best time", main, "Attempts left", second, "Made it", hits + "/" + tries);
			case "crystal_survive" -> map("Survived", main + "s", "Time left", second, "Bot pops", String.valueOf(attacker == null ? 0 : attacker.pops));
			default -> map(capital(drill.unit), main, "Time left", second, "Best hit", fmt(bestSingle));
		};
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private void finish() {
		SessionManager.reportDrill(this, modeId, drill, Math.round(score * 10) / 10.0);
		end();
	}
}
