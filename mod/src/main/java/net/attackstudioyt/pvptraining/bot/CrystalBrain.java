package net.attackstudioyt.pvptraining.bot;

import java.util.List;
import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionImpl;

/**
 * Crystal PvP on real, breakable ground. The bot plays the kit the way a player does:
 * obsidian then crystal then hit, respawn anchors for burst, a totem always in the off
 * hand, pearls to chase or to get out, and it punishes a popped totem immediately.
 * Speed and judgement scale with {@link Skill}.
 */
public class CrystalBrain extends BotBrain {
	private enum Anchor { NONE, PLACED, CHARGED }

	private int nextActionAt;
	private EndCrystalEntity pending;
	private int breakAt;
	private BlockPos obsidianReady;

	private Anchor anchor = Anchor.NONE;
	private BlockPos anchorPos;
	private int anchorStepAt;
	private int anchorHolds;

	private int retotemAt = -1;
	private boolean hadTotem = true;
	private boolean targetHadTotem = true;
	private int targetPoppedAt = -9999;
	private int nextPearlAt;
	private int nextHitCrystalAt;
	private int comboUntil;
	private int pearlAimTicks;
	private Vec3d pearlAim;

	/** Totems this bot has popped since the round began. */
	public int pops;

	public CrystalBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target) {
		super(bot, arena, skill, target);
	}

	@Override
	public void reset() {
		super.reset();
		pending = null;
		obsidianReady = null;
		anchor = Anchor.NONE;
		retotemAt = -1;
		hadTotem = true;
		targetHadTotem = true;
		targetPoppedAt = -9999;
		pearlAimTicks = 0;
		nextActionAt = age + 20;
		nextPearlAt = age + 60;
		nextHitCrystalAt = age + 40;
		comboUntil = 0;
	}

	@Override
	protected void think() {
		if (target == null || !target.isAlive()) {
			bot.stopMoving();
			return;
		}
		ServerWorld world = (ServerWorld) bot.getEntityWorld();
		tickTotem();
		watchTarget();
		if (tickPearl()) return;

		boolean noTotemsLeft = !bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && !hasSpare(Items.TOTEM_OF_UNDYING);
		boolean hurt = bot.getHealth() + bot.getAbsorptionAmount() <= skill.eatBelow();

		// Out of totems and low: get out first, heal second.
		if (noTotemsLeft && hurt && distanceToTarget() < 9 && throwPearl(escapePoint())) return;
		if (bot.isOnGround() && (distanceToTarget() > 5 || noTotemsLeft) && eatIfNeeded()) return;
		// The trainee pearled or ran: close the gap the same way.
		if (distanceToTarget() > 13 && bot.isOnGround() && throwPearl(target.getEntityPos())) return;

		faceTarget();
		// Normally it fights from crystal range; when a hit-crystal is due it steps in to sword range.
		boolean wantsHit = age >= nextHitCrystalAt && skill.difficulty >= 3;
		approach(wantsHit ? 2.4 : 4.0, distanceToTarget() > 7 || wantsHit);
		dodgeBlasts(world);

		if (pending != null && (pending.isRemoved() || !pending.isAlive())) pending = null;
		if (tickAnchor(world)) return;

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
		if (pending == null && wantsHit && tryHitCrystal()) return;
		if (pending != null || age < nextActionAt) return;

		// The moment a totem pops is the moment to hit again: skip the usual pacing.
		boolean punish = age - targetPoppedAt < 16 || age < comboUntil;

		if (obsidianReady != null) {
			BlockPos base = obsidianReady;
			obsidianReady = null;
			if (canHoldCrystal(world, base) && score(crystalPos(base), 6) > 4) {
				placeCrystal(world, base, punish);
				return;
			}
		}
		BlockPos direct = bestCrystalBase(world, false);
		if (direct != null) {
			placeCrystal(world, direct, punish);
			return;
		}
		boolean wantAnchor = punish || random.nextFloat() < 0.3F + skill.t * 0.25F;
		if (wantAnchor && startAnchor(world)) return;
		BlockPos needsObsidian = bestCrystalBase(world, true);
		if (needsObsidian != null && placeBlock(world, needsObsidian, Items.OBSIDIAN, Blocks.OBSIDIAN.getDefaultState())) {
			obsidianReady = needsObsidian;
			nextActionAt = age + (punish ? 1 : Math.max(1, skill.crystalPlaceDelay() / 2));
			return;
		}
		if (startAnchor(world)) return;

		if (distanceToTarget() < 3.2 && bot.getAttackCooldownProgress(0.5F) > 0.95F && inReach(3.0)) {
			bot.select(Items.NETHERITE_SWORD);
			bot.hit(target);
		}
	}

	/**
	 * Hit-crystal: a sprint hit pops the trainee off the ground, then obsidian and a crystal go
	 * in underneath while they are still in the air. An airborne player has nothing between the
	 * blast and their legs, so the same crystal hurts far more than it would on the ground.
	 */
	private boolean tryHitCrystal() {
		if (!target.isOnGround() || distanceToTarget() > 3.1 || !inReach(3.0)) return false;
		if (bot.getAttackCooldownProgress(0.5F) < 0.95F || !canSee(target.getEyePos())) return false;
		bot.select(Items.NETHERITE_SWORD);
		bot.setSprinting(true);
		bot.hit(target);
		comboUntil = age + 12;
		nextActionAt = age + 1 + skill.reactionTicks() / 3;
		nextHitCrystalAt = age + 30 + Math.round((1 - skill.t) * 90) + random.nextInt(20);
		return true;
	}

	/* ------------------------------------------------------------ awareness */

	private void tickTotem() {
		boolean has = bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
		if (hadTotem && !has) {
			pops++;
			retotemAt = age + skill.retotemDelay();
		}
		hadTotem = has;
		if (!has && retotemAt >= 0 && age >= retotemAt) {
			int slot = find(Items.TOTEM_OF_UNDYING);
			if (slot >= 0) {
				bot.equipStack(EquipmentSlot.OFFHAND, bot.getInventory().getStack(slot).copy());
				bot.getInventory().setStack(slot, ItemStack.EMPTY);
				hadTotem = true;
			}
			retotemAt = -1;
		}
	}

	private void watchTarget() {
		boolean has = target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || target.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
		if (targetHadTotem && !has) targetPoppedAt = age + Math.min(skill.reactionTicks(), 6);
		targetHadTotem = has;
	}

	/** Steps away from a blast that is about to hurt the bot more than the trainee. */
	private void dodgeBlasts(ServerWorld world) {
		if (skill.difficulty < 3) return;
		Vec3d threat = null;
		for (EndCrystalEntity c : world.getEntitiesByClass(EndCrystalEntity.class, bot.getBoundingBox().expand(3.4), e -> e.isAlive() && e != pending)) {
			if (blastDamage(c.getEntityPos(), bot, 6) > blastDamage(c.getEntityPos(), target, 6)) threat = c.getEntityPos();
		}
		if (threat == null) {
			BlockPos feet = bot.getBlockPos();
			for (BlockPos p : BlockPos.iterate(feet.add(-2, -1, -2), feet.add(2, 1, 2))) {
				BlockState s = world.getBlockState(p);
				if (s.isOf(Blocks.RESPAWN_ANCHOR) && s.get(RespawnAnchorBlock.CHARGES) > 0 && !p.equals(anchorPos)) threat = Vec3d.ofCenter(p);
			}
		}
		if (threat == null) return;
		float yawAway = (float) (MathHelper.atan2(bot.getZ() - threat.z, bot.getX() - threat.x) * 180.0 / Math.PI) - 90.0F;
		double rad = Math.toRadians(MathHelper.wrapDegrees(yawAway - bot.getYaw()));
		bot.inForward = (float) Math.cos(rad);
		bot.inStrafe = (float) -Math.sin(rad);
		bot.setSprinting(true);
		stayInArena();
	}

	/* ---------------------------------------------------------------- pearls */

	private Vec3d escapePoint() {
		Vec3d away = bot.getEntityPos().subtract(target.getEntityPos()).multiply(1, 0, 1);
		if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
		Vec3d point = bot.getEntityPos().add(away.normalize().multiply(16));
		// stay inside the walls: pull the landing spot toward the middle if needed
		for (int i = 0; i < 6 && arena.distanceToEdge(point) < 3; i++) point = point.lerp(arena.center(), 0.3);
		return point;
	}

	private boolean throwPearl(Vec3d to) {
		if (age < nextPearlAt || bot.hotbarSlot(Items.ENDER_PEARL) < 0) return false;
		if (bot.getItemCooldownManager().isCoolingDown(bot.getInventory().getStack(bot.hotbarSlot(Items.ENDER_PEARL)))) return false;
		pearlAim = to;
		pearlAimTicks = 1;
		nextPearlAt = age + 60 + Math.round((1 - skill.t) * 160);
		return true;
	}

	/** Holds the throw angle for a couple of ticks so the pearl leaves on the intended arc. */
	private boolean tickPearl() {
		if (pearlAimTicks == 0) return false;
		bot.stopMoving();
		bot.select(Items.ENDER_PEARL);
		Vec3d eye = bot.getEyePos();
		double dx = pearlAim.x - eye.x;
		double dz = pearlAim.z - eye.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		double dy = pearlAim.y - eye.y;
		// pearl: 1.5 blocks/tick, 0.03 gravity. Small-angle ballistic solution plus the height difference.
		double lift = Math.toDegrees(0.5 * Math.asin(MathHelper.clamp(0.03 * flat / 2.25, -1, 1)));
		float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float) -(Math.toDegrees(Math.atan2(dy, Math.max(flat, 0.1))) + lift + 2);
		bot.look(yaw, pitch);
		if (++pearlAimTicks >= 3) {
			bot.use(Hand.MAIN_HAND);
			pearlAimTicks = 0;
		}
		return true;
	}

	/* --------------------------------------------------------------- crystals */

	private static Vec3d crystalPos(BlockPos base) {
		return new Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
	}

	private void placeCrystal(ServerWorld world, BlockPos base, boolean rushed) {
		if (!bot.select(Items.END_CRYSTAL) && !(refill(Items.END_CRYSTAL, 3) && bot.select(Items.END_CRYSTAL))) return;
		bot.lookAt(Vec3d.ofCenter(base).add(0, 0.5, 0), 180);
		bot.getMainHandStack().decrement(1);
		EndCrystalEntity crystal = new EndCrystalEntity(world, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
		crystal.setShowBottom(false);
		world.spawnEntity(crystal);
		bot.swingHand(Hand.MAIN_HAND);
		pending = crystal;
		breakAt = age + (rushed ? 1 : skill.crystalBreakDelay());
		nextActionAt = age + (rushed ? 2 : skill.crystalPlaceDelay() + random.nextInt(3));
	}

	private void pop(EndCrystalEntity crystal) {
		bot.lookAt(crystal.getEntityPos().add(0, 0.8, 0), 180);
		bot.hit(crystal);
	}

	/**
	 * Best block to stand a crystal on. With {@code needsObsidian} it looks for open ground
	 * beside the trainee where an obsidian block could go first.
	 */
	private BlockPos bestCrystalBase(ServerWorld world, boolean needsObsidian) {
		BlockPos around = target.getBlockPos();
		BlockPos best = null;
		double bestScore = 8;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -2; dy <= 1; dy++) {
					BlockPos base = around.add(dx, dy, dz);
					if (needsObsidian ? !canTakeBlock(world, base, true) : !canHoldCrystal(world, base)) continue;
					Vec3d pos = crystalPos(base);
					if (bot.getEyePos().distanceTo(pos) > 4.4) continue;
					double s = score(pos, 6);
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

	/** An empty block with something solid under it and nobody standing in it. */
	private boolean canTakeBlock(ServerWorld world, BlockPos pos, boolean needsRoomAbove) {
		if (!world.isAir(pos) || world.isAir(pos.down())) return false;
		if (needsRoomAbove && !world.isAir(pos.up())) return false;
		if (arena.distanceToEdge(Vec3d.ofCenter(pos)) < 0.5) return false;
		return world.getOtherEntities(null, new Box(pos).expand(0, needsRoomAbove ? 1 : 0, 0)).isEmpty();
	}

	private boolean placeBlock(ServerWorld world, BlockPos pos, Item item, BlockState state) {
		if (bot.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > 4.5) return false;
		if (!bot.select(item) && !(refill(item, 4) && bot.select(item))) return false;
		bot.lookAt(Vec3d.ofCenter(pos), 180);
		bot.getMainHandStack().decrement(1);
		world.setBlockState(pos, state);
		world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1, 0.8F);
		bot.swingHand(Hand.MAIN_HAND);
		return true;
	}

	private EndCrystalEntity bestExistingCrystal(ServerWorld world) {
		List<EndCrystalEntity> near = world.getEntitiesByClass(EndCrystalEntity.class, bot.getBoundingBox().expand(4.5), e -> e.isAlive());
		EndCrystalEntity best = null;
		double bestScore = 6;
		for (EndCrystalEntity c : near) {
			// a crystal the trainee just placed is not "seen" until the bot's reaction time has passed
			if (c.age < skill.crystalBreakDelay() + skill.reactionTicks()) continue;
			if (bot.getEyePos().distanceTo(c.getEntityPos().add(0, 0.8, 0)) > 4.2) continue;
			double s = score(c.getEntityPos(), 6);
			if (s > bestScore) {
				bestScore = s;
				best = c;
			}
		}
		return best;
	}

	/* ---------------------------------------------------------------- anchors */

	private boolean startAnchor(ServerWorld world) {
		if (find(Items.RESPAWN_ANCHOR) < 0 || find(Items.GLOWSTONE) < 0) return false;
		BlockPos around = target.getBlockPos();
		BlockPos best = null;
		double bestScore = 10;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = around.add(dx, dy, dz);
					if (!canTakeBlock(world, pos, false)) continue;
					double s = score(Vec3d.ofCenter(pos), 5);
					if (s > bestScore) {
						bestScore = s;
						best = pos;
					}
				}
			}
		}
		if (best == null || !placeBlock(world, best, Items.RESPAWN_ANCHOR, Blocks.RESPAWN_ANCHOR.getDefaultState())) return false;
		anchor = Anchor.PLACED;
		anchorHolds = 0;
		anchorPos = best;
		anchorStepAt = age + Math.max(1, skill.crystalBreakDelay());
		return true;
	}

	/** Place, charge with glowstone, detonate: the same three clicks a player makes. */
	private boolean tickAnchor(ServerWorld world) {
		if (anchor == Anchor.NONE) return false;
		if (!world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
			anchor = Anchor.NONE;
			anchorPos = null;
			return false;
		}
		if (age < anchorStepAt) return true;
		bot.lookAt(Vec3d.ofCenter(anchorPos), 180);
		if (anchor == Anchor.PLACED) {
			if (!bot.select(Items.GLOWSTONE) && !(refill(Items.GLOWSTONE, 2) && bot.select(Items.GLOWSTONE))) {
				anchor = Anchor.NONE;
				return false;
			}
			bot.getMainHandStack().decrement(1);
			world.setBlockState(anchorPos, world.getBlockState(anchorPos).with(RespawnAnchorBlock.CHARGES, 1));
			world.playSound(null, anchorPos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1, 1);
			bot.swingHand(Hand.MAIN_HAND);
			anchor = Anchor.CHARGED;
			anchorStepAt = age + Math.max(1, skill.crystalBreakDelay());
			return true;
		}
		// Back out if the trainee moved and this would now cost the bot more than it deals.
		// It waits a moment for a better angle, then gives the anchor up rather than eat its own blast.
		if (score(Vec3d.ofCenter(anchorPos), 5) < 0 && skill.difficulty >= 5) {
			if (++anchorHolds > 8) {
				anchor = Anchor.NONE;
				anchorPos = null;
			} else anchorStepAt = age + 4;
			return false;
		}
		bot.select(Items.NETHERITE_SWORD);
		bot.swingHand(Hand.MAIN_HAND);
		BlockPos at = anchorPos;
		world.removeBlock(at, false);
		world.createExplosion(bot, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 5.0F, true, World.ExplosionSourceType.BLOCK);
		anchor = Anchor.NONE;
		anchorPos = null;
		nextActionAt = age + skill.crystalPlaceDelay();
		return true;
	}

	/* --------------------------------------------------------------- scoring */

	private double score(Vec3d blast, float power) {
		double toTarget = blastDamage(blast, target, power);
		double toSelf = blastDamage(blast, bot, power);
		boolean safe = bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || toSelf * 0.3 < bot.getHealth() + bot.getAbsorptionAmount() - 3;
		if (!safe) return -1;
		// Only a Rookie takes a trade that hurts it more than the trainee.
		if (skill.difficulty >= 4 && toSelf > toTarget) return -1;
		// Better players care more about what a blast costs them, so they favour the far side of the target.
		return toTarget - toSelf * skill.selfDamageWeight();
	}

	/**
	 * Vanilla explosion curve before armour, including real line-of-sight exposure. Without
	 * exposure the bot happily bombs a trainee who is safe in a crater while it stands in the open.
	 */
	private static double blastDamage(Vec3d at, LivingEntity who, float power) {
		double reach = power * 2.0;
		double dist = Math.sqrt(who.squaredDistanceTo(at)) / reach;
		if (dist > 1) return 0;
		double impact = (1.0 - dist) * ExplosionImpl.calculateReceivedDamage(at, who);
		return (impact * impact + impact) / 2.0 * 7.0 * reach + 1.0;
	}

	/* -------------------------------------------------------------- inventory */

	private int find(Item item) {
		for (int i = 0; i < bot.getInventory().size(); i++) if (bot.getInventory().getStack(i).isOf(item)) return i;
		return -1;
	}

	private boolean hasSpare(Item item) {
		return find(item) >= 0;
	}

	/** Moves a spare stack from the backpack into the given hotbar slot. */
	private boolean refill(Item item, int hotbarSlot) {
		for (int i = 9; i < bot.getInventory().size(); i++) {
			ItemStack stack = bot.getInventory().getStack(i);
			if (!stack.isOf(item)) continue;
			bot.getInventory().setStack(hotbarSlot, stack.copy());
			bot.getInventory().setStack(i, ItemStack.EMPTY);
			return true;
		}
		return false;
	}
}
