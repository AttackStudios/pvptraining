package net.attackstudioyt.pvptraining.bot;

import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Fights with the Mace kit. The same brain drives all three Mace-family duels:
 * {@code SPEAR} adds Lunge gap-closers, {@code ELYTRA} adds the full climb, dive,
 * wings-off, smash sequence. Everything is paced by {@link Skill}.
 */
public class MaceBrain extends BotBrain {
	public enum Variant { MACE, SPEAR, ELYTRA }

	private enum Dive { NONE, LAUNCH, RISE, FALL }
	private enum Ely { NONE, TAKEOFF, CLIMB, DIVE, DROP }

	private final Variant variant;

	private Dive dive = Dive.NONE;
	private int diveTicks;
	private int nextDiveAt;

	private Ely ely = Ely.NONE;
	private int elyTicks;
	private int nextElyAt;
	private double elyStartY;
	private int rocketsFired;
	private int lastRocketAt;

	private int nextLungeAt;
	private int critTicks = -1;
	private int sprintResetTicks;
	private int shieldTicks;

	public MaceBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target, Variant variant) {
		super(bot, arena, skill, target);
		this.variant = variant;
		this.nextDiveAt = 40 + random.nextInt(40);
		this.nextElyAt = 60 + random.nextInt(60);
		this.nextLungeAt = 30;
	}

	@Override
	public void reset() {
		super.reset();
		dive = Dive.NONE;
		ely = Ely.NONE;
		critTicks = -1;
		shieldTicks = 0;
		nextDiveAt = age + 40 + random.nextInt(40);
		nextElyAt = age + 60 + random.nextInt(60);
		nextLungeAt = age + 30;
		if (bot.isGliding()) bot.stopGliding();
		bot.wearFromInventory(Items.NETHERITE_CHESTPLATE);
	}

	@Override
	protected void think() {
		if (target == null || !target.isAlive()) {
			bot.stopMoving();
			return;
		}
		if (ely != Ely.NONE) {
			tickElytra();
			return;
		}
		if (dive != Dive.NONE) {
			tickDive();
			return;
		}
		if (bot.isOnGround() && eatIfNeeded()) return;
		if (tickShield()) return;

		if (variant == Variant.ELYTRA && age >= nextElyAt && startElytra()) return;
		if (age >= nextDiveAt && startDive()) return;
		if (variant == Variant.SPEAR && age >= nextLungeAt && tryLunge()) return;

		melee();
	}

	/* ------------------------------------------------------------------ melee */

	private void melee() {
		boolean targetShielding = target instanceof PlayerEntity p && p.isBlocking();
		bot.select(targetShielding ? Items.NETHERITE_AXE : Items.NETHERITE_SWORD);
		faceTarget();
		approach(2.6, sprintResetTicks-- <= 0);

		float charge = bot.getAttackCooldownProgress(0.5F);
		boolean ready = charge >= 0.95F || (charge > 0.55F && random.nextFloat() < skill.earlySwingChance() * 0.08F);
		double dist = distanceToTarget();

		// set up a crit: jump a few ticks before the weapon is charged, swing on the way down
		if (critTicks < 0 && bot.isOnGround() && dist < 3.8 && charge > 0.6F && charge < 0.95F && random.nextFloat() < skill.critChance() * 0.35F) {
			bot.inJump = true;
			critTicks = 0;
			return;
		}
		bot.inJump = false;
		if (critTicks >= 0) {
			critTicks++;
			boolean falling = bot.getVelocity().y < -0.08 && !bot.isOnGround();
			if (falling && ready && inReach(3.0)) {
				bot.setSprinting(false);
				strike();
				critTicks = -1;
			} else if (critTicks > 16 || (bot.isOnGround() && critTicks > 3)) critTicks = -1;
			return;
		}
		if (ready && inReach(3.0) && canSee(target.getEyePos())) strike();
	}

	private void strike() {
		bot.hit(target);
		sprintResetTicks = 2;
	}

	/** Raise the shield when the trainee is dropping in with a mace. Returns true while blocking. */
	private boolean tickShield() {
		if (shieldTicks > 0) {
			shieldTicks--;
			faceTarget();
			bot.inForward = 0;
			bot.inStrafe = 0;
			boolean threatOver = target.isOnGround() || distanceToTarget() > 8;
			if (shieldTicks == 0 || threatOver) {
				bot.clearActiveItem();
				shieldTicks = 0;
			}
			return shieldTicks > 0;
		}
		boolean incoming = !target.isOnGround() && target.getVelocity().y < -0.4 && target.getY() > bot.getY() + 2.5
			&& horizontalDistance(target.getEntityPos()) < 5 && target.getMainHandStack().isOf(Items.MACE);
		if (!incoming || !bot.isOnGround() || random.nextFloat() > skill.shieldChance()) return false;
		if (!bot.select(Items.SHIELD)) return false;
		bot.use(Hand.MAIN_HAND);
		shieldTicks = 24;
		return true;
	}

	/* -------------------------------------------------------- wind charge dive */

	private boolean startDive() {
		double dist = distanceToTarget();
		if (!bot.isOnGround() || dist < 1.2 || dist > 6.5) return false;
		if (bot.hotbarSlot(Items.WIND_CHARGE) < 0 || bot.hotbarSlot(Items.MACE) < 0) return false;
		if (bot.getItemCooldownManager().isCoolingDown(bot.getInventory().getStack(bot.hotbarSlot(Items.WIND_CHARGE)))) return false;
		dive = Dive.LAUNCH;
		diveTicks = 0;
		bot.stopMoving();
		bot.select(Items.WIND_CHARGE);
		return true;
	}

	private void tickDive() {
		diveTicks++;
		switch (dive) {
			case LAUNCH -> {
				bot.look(bot.getYaw(), 90);
				bot.inJump = diveTicks == 2;
				if (diveTicks == 3) bot.use(Hand.MAIN_HAND);
				if (diveTicks >= 5) {
					dive = Dive.RISE;
					bot.inJump = false;
				}
			}
			case RISE -> {
				bot.select(Items.NETHERITE_SWORD);
				steerOver();
				if (bot.getVelocity().y <= 0.05 && diveTicks > 7) dive = Dive.FALL;
				if (bot.isOnGround() && diveTicks > 12) endDive();
			}
			case FALL -> {
				selectMace();
				steerOver();
				if (bot.getVelocity().y < -0.08 && inReach(3.4) && bot.fallDistance > 1.5F) {
					bot.setSprinting(false);
					bot.hit(target);
					endDive();
					return;
				}
				if (bot.isOnGround() || diveTicks > 60) endDive();
			}
			default -> endDive();
		}
	}

	private void endDive() {
		dive = Dive.NONE;
		nextDiveAt = age + skill.diveCooldown() + random.nextInt(30);
		bot.inJump = false;
	}

	private void selectMace() {
		// The density mace is the one-shot tool; fall back to whichever mace is on the bar.
		for (int i = 0; i < 9; i++) {
			if (bot.getInventory().getStack(i).isOf(Items.MACE) && i == 7) {
				bot.getInventory().setSelectedSlot(i);
				return;
			}
		}
		bot.select(Items.MACE);
	}

	/** Air control: drift over the target, then stall so the fall lands on top of them. */
	private void steerOver() {
		faceTarget();
		bot.setSprinting(false);
		bot.inStrafe = 0;
		bot.inForward = horizontalDistance(perceivedTarget()) > 1.2 ? 1.0F : -0.15F;
	}

	/* ------------------------------------------------------------ spear lunge */

	private boolean tryLunge() {
		double dist = distanceToTarget();
		if (!bot.isOnGround() || dist < 5 || dist > 16 || !canSee(target.getEyePos())) return false;
		if (!bot.select(Items.NETHERITE_SPEAR)) return false;
		Vec3d aim = perceivedTarget().add(0, 1.0, 0);
		if (bot.lookAt(aim, 180) > 6) return true; // spend this tick turning
		bot.setSprinting(true);
		bot.stab();
		// An Ace re-lunges almost immediately, the way a player does with attribute swapping.
		nextLungeAt = age + skill.lungeCooldown() + random.nextInt(8);
		return true;
	}

	/* ---------------------------------------------------------- elytra + mace */

	private boolean startElytra() {
		double dist = distanceToTarget();
		if (!bot.isOnGround() || dist < 6 || dist > 45) return false;
		if (bot.hotbarSlot(Items.FIREWORK_ROCKET) < 0 || bot.hotbarSlot(Items.MACE) < 0) return false;
		if (!bot.wearFromInventory(Items.ELYTRA)) return false;
		ely = Ely.TAKEOFF;
		elyTicks = 0;
		rocketsFired = 0;
		elyStartY = bot.getY();
		bot.stopMoving();
		return true;
	}

	private void tickElytra() {
		elyTicks++;
		switch (ely) {
			case TAKEOFF -> {
				bot.look(bot.getYaw(), -70);
				bot.inJump = elyTicks <= 2;
				if (elyTicks >= 4 && !bot.isOnGround()) {
					bot.inJump = false;
					bot.startGliding();
					bot.select(Items.FIREWORK_ROCKET);
					bot.use(Hand.MAIN_HAND);
					rocketsFired = 1;
					lastRocketAt = age;
					ely = Ely.CLIMB;
				} else if (elyTicks > 12) abortElytra();
			}
			case CLIMB -> {
				// climb away from the target so the dive has room to line up
				Vec3d away = bot.getEntityPos().subtract(target.getEntityPos()).multiply(1, 0, 1);
				float yaw = away.lengthSquared() < 0.01 ? bot.getYaw() : (float) (Math.atan2(away.z, away.x) * 180.0 / Math.PI) - 90.0F;
				bot.look(yaw, -72);
				double climbed = bot.getY() - elyStartY;
				if (climbed < skill.elytraClimb() && rocketsFired < 3 && age - lastRocketAt > 14) {
					bot.select(Items.FIREWORK_ROCKET);
					bot.use(Hand.MAIN_HAND);
					rocketsFired++;
					lastRocketAt = age;
				}
				// hang for a moment at the top, like a player waiting for the rocket to burn out
				if (climbed >= skill.elytraClimb() || elyTicks > 70) {
					ely = Ely.DIVE;
					elyTicks = 0;
				}
				if (!bot.isGliding() && elyTicks > 8) abortElytra();
			}
			case DIVE -> {
				bot.select(Items.NETHERITE_SWORD);
				Vec3d aim = perceivedTarget().add(0, 1.0, 0);
				bot.lookAt(aim, 14 + skill.turnSpeed() * 0.4F);
				if (skill.difficulty >= 6 && elyTicks == 12 && bot.getPitch() > 35) {
					bot.select(Items.FIREWORK_ROCKET);
					bot.use(Hand.MAIN_HAND);
				}
				double height = bot.getY() - target.getY();
				double flat = horizontalDistance(target.getEntityPos());
				boolean closeEnough = (height < 12 && flat < 5) || bot.getEntityPos().distanceTo(target.getEntityPos()) < 9;
				if (closeEnough && height > 2.5) {
					// wings off: gliding blocks the smash, and free fall is what builds fall distance
					bot.stopGliding();
					bot.wearFromInventory(Items.NETHERITE_CHESTPLATE);
					ely = Ely.DROP;
					elyTicks = 0;
				} else if (!bot.isGliding() || bot.isOnGround() || elyTicks > 120) abortElytra();
			}
			case DROP -> {
				selectMace();
				steerOver();
				if (bot.getVelocity().y < -0.08 && inReach(3.5) && bot.fallDistance > 1.5F) {
					bot.hit(target);
					abortElytra();
					return;
				}
				if (bot.isOnGround() || elyTicks > 50) abortElytra();
			}
			default -> abortElytra();
		}
	}

	private void abortElytra() {
		if (bot.isGliding()) bot.stopGliding();
		bot.wearFromInventory(Items.NETHERITE_CHESTPLATE);
		bot.inJump = false;
		ely = Ely.NONE;
		nextElyAt = age + skill.elytraCooldown() + random.nextInt(60);
	}
}
