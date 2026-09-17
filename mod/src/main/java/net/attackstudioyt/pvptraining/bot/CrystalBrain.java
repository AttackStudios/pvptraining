package net.attackstudioyt.pvptraining.bot;

import java.util.List;
import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Crystal PvP: place on obsidian next to the trainee, pop it, keep a totem in the
 * off hand, eat when it is safe. Place and break speed, re-totem speed and how much
 * self-damage it tolerates all scale with {@link Skill}.
 */
public class CrystalBrain extends BotBrain {
	private int nextPlaceAt;
	private int retotemAt = -1;
	private EndCrystalEntity pending;
	private int breakAt;
	/** Totems this bot has popped since the round began. */
	public int pops;
	private boolean hadTotem = true;

	public CrystalBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target) {
		super(bot, arena, skill, target);
	}

	@Override
	public void reset() {
		super.reset();
		pending = null;
		retotemAt = -1;
		hadTotem = true;
		nextPlaceAt = age + 20;
	}

	@Override
	protected void think() {
		if (target == null || !target.isAlive()) {
			bot.stopMoving();
			return;
		}
		tickTotem();
		if (bot.isOnGround() && distanceToTarget() > 5 && eatIfNeeded()) return;

		faceTarget();
		approach(3.6, distanceToTarget() > 7);

		ServerWorld world = (ServerWorld) bot.getEntityWorld();
		if (pending != null && (pending.isRemoved() || !pending.isAlive())) pending = null;

		// break: our own pending crystal once its delay is up, or any crystal that favours us
		if (pending != null && age >= breakAt) {
			pop(pending);
			pending = null;
			return;
		}
		if (pending == null && age % 2 == 0) {
			EndCrystalEntity best = bestExistingCrystal(world);
			if (best != null) {
				pop(best);
				return;
			}
		}

		if (pending == null && age >= nextPlaceAt) {
			BlockPos spot = bestPlacement(world);
			if (spot != null) place(world, spot);
			else if (distanceToTarget() < 3.2 && bot.getAttackCooldownProgress(0.5F) > 0.95F && inReach(3.0)) {
				bot.select(Items.NETHERITE_SWORD);
				bot.hit(target);
			}
		}
	}

	private void tickTotem() {
		boolean has = bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
		if (hadTotem && !has) {
			pops++;
			retotemAt = age + skill.retotemDelay();
		}
		hadTotem = has;
		if (!has && retotemAt >= 0 && age >= retotemAt) {
			for (int i = 0; i < bot.getInventory().size(); i++) {
				ItemStack stack = bot.getInventory().getStack(i);
				if (!stack.isOf(Items.TOTEM_OF_UNDYING)) continue;
				bot.equipStack(EquipmentSlot.OFFHAND, stack.copy());
				bot.getInventory().setStack(i, ItemStack.EMPTY);
				hadTotem = true;
				break;
			}
			retotemAt = -1;
		}
	}

	private void place(ServerWorld world, BlockPos base) {
		if (!bot.select(Items.END_CRYSTAL)) {
			refillCrystals();
			return;
		}
		Vec3d top = Vec3d.ofCenter(base).add(0, 0.5, 0);
		bot.lookAt(top, 180);
		bot.getMainHandStack().decrement(1);
		EndCrystalEntity crystal = new EndCrystalEntity(world, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
		crystal.setShowBottom(false);
		world.spawnEntity(crystal);
		bot.swingHand(Hand.MAIN_HAND);
		pending = crystal;
		breakAt = age + skill.crystalBreakDelay();
		nextPlaceAt = age + skill.crystalPlaceDelay() + random.nextInt(3);
	}

	private void refillCrystals() {
		for (int i = 9; i < bot.getInventory().size(); i++) {
			ItemStack stack = bot.getInventory().getStack(i);
			if (!stack.isOf(Items.END_CRYSTAL)) continue;
			bot.getInventory().setStack(3, stack.copy());
			bot.getInventory().setStack(i, ItemStack.EMPTY);
			return;
		}
	}

	private void pop(EndCrystalEntity crystal) {
		bot.lookAt(crystal.getEntityPos().add(0, 0.8, 0), 180);
		bot.hit(crystal);
	}

	/* --------------------------------------------------------------- scoring */

	private double score(Vec3d crystalPos) {
		double toTarget = blastDamage(crystalPos, target);
		double toSelf = blastDamage(crystalPos, bot);
		boolean safe = bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || toSelf < bot.getHealth() + bot.getAbsorptionAmount() - 3;
		if (!safe) return -1;
		return toTarget - toSelf * (1.0 - skill.selfDamageWeight() * 0.6);
	}

	/** Vanilla explosion curve (power 6), before armour. Exposure is treated as full on open ground. */
	private static double blastDamage(Vec3d at, LivingEntity who) {
		double dist = Math.sqrt(who.squaredDistanceTo(at)) / 12.0;
		if (dist > 1) return 0;
		double impact = 1.0 - dist;
		return (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
	}

	private BlockPos bestPlacement(ServerWorld world) {
		BlockPos around = target.getBlockPos();
		BlockPos best = null;
		double bestScore = 8; // not worth a crystal below this
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -2; dy <= 1; dy++) {
					BlockPos base = around.add(dx, dy, dz);
					if (!canHoldCrystal(world, base)) continue;
					Vec3d pos = new Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
					if (bot.getEyePos().distanceTo(pos) > 4.4) continue;
					double s = score(pos);
					if (s > bestScore) {
						bestScore = s;
						best = base;
					}
				}
			}
		}
		return best;
	}

	private boolean canHoldCrystal(ServerWorld world, BlockPos base) {
		BlockState state = world.getBlockState(base);
		if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return false;
		if (!world.isAir(base.up())) return false;
		Box box = new Box(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1);
		return world.getOtherEntities(null, box).isEmpty();
	}

	private EndCrystalEntity bestExistingCrystal(ServerWorld world) {
		List<EndCrystalEntity> near = world.getEntitiesByClass(EndCrystalEntity.class, bot.getBoundingBox().expand(4.5), e -> e.isAlive());
		EndCrystalEntity best = null;
		double bestScore = 6;
		for (EndCrystalEntity c : near) {
			// a crystal the trainee just placed is not "seen" until the bot's reaction time has passed
			if (c.age < skill.crystalBreakDelay() + skill.reactionTicks()) continue;
			if (bot.getEyePos().distanceTo(c.getEntityPos().add(0, 0.8, 0)) > 4.2) continue;
			double s = score(c.getEntityPos());
			if (s > bestScore) {
				bestScore = s;
				best = c;
			}
		}
		return best;
	}
}
