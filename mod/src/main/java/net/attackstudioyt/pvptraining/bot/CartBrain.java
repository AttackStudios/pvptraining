package net.attackstudioyt.pvptraining.bot;

import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Cart PvP. Between carts it sword-fights; when it has an opening it carts the way players do:
 * rail beside the opponent, TNT minecart on the rail, then a burning arrow. A TNT minecart goes
 * off instantly when a burning projectile hits it, and the faster the arrow the bigger the blast.
 * <p>
 * In X-Bow mode the arrow comes from a loaded crossbow instead. Crossbows cannot have Flame, so
 * the bot does what players do: lights a fire in front of the cart and shoots through it.
 */
public class CartBrain extends BotBrain {
	private enum Step { NONE, DRAW, WAIT, RELOAD }

	private final boolean crossbow;
	private Step step = Step.NONE;
	private int stepTicks;
	private int nextCartAt;
	private TntMinecartEntity cart;
	private BlockPos firePos;
	private int critTicks = -1;

	public CartBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target, boolean crossbow) {
		super(bot, arena, skill, target);
		this.crossbow = crossbow;
		this.nextCartAt = 40;
	}

	@Override
	public void reset() {
		super.reset();
		step = Step.NONE;
		cart = null;
		firePos = null;
		critTicks = -1;
		nextCartAt = age + 40;
	}

	@Override
	protected void think() {
		if (target == null || !target.isAlive()) {
			bot.stopMoving();
			return;
		}
		if (step != Step.NONE) {
			tickCart();
			return;
		}
		if (bot.isOnGround() && distanceToTarget() > 5 && eatIfNeeded()) return;
		if (crossbow && needsReload() && distanceToTarget() > 4.5) {
			beginReload();
			return;
		}
		if (age >= nextCartAt && bot.isOnGround() && startCart()) return;
		melee();
	}

	/* ------------------------------------------------------------------ melee */

	private void melee() {
		bot.select(Items.DIAMOND_SWORD);
		faceTarget();
		// Stay at the edge of sword range: close enough to threaten, far enough to cart safely.
		approach(3.1, true);
		float charge = bot.getAttackCooldownProgress(0.5F);
		if (critTicks < 0 && bot.isOnGround() && distanceToTarget() < 3.6 && charge > 0.55F && charge < 0.9F && random.nextFloat() < skill.critChance() * 0.2F) {
			bot.inJump = true;
			critTicks = 0;
			return;
		}
		if (critTicks >= 0) {
			critTicks++;
			bot.inJump = false;
			if (bot.getVelocity().y < -0.08 && !bot.isOnGround() && charge >= 0.95F && inReach(3.0)) {
				bot.setSprinting(false);
				bot.hit(target);
				critTicks = -1;
			} else if (critTicks > 16 || (bot.isOnGround() && critTicks > 3)) critTicks = -1;
			return;
		}
		if (charge >= 0.95F && inReach(3.0) && canSee(target.getEyePos())) bot.hit(target);
	}

	/* ------------------------------------------------------------------- carts */

	private boolean startCart() {
		double dist = distanceToTarget();
		if (dist < 3.0 || dist > 7.5 || !canSee(target.getEyePos())) return false;
		if (find(Items.RAIL) < 0 || find(Items.TNT_MINECART) < 0) return false;
		if (crossbow ? !hasLoadedCrossbow() && find(Items.BOW) < 0 : find(Items.BOW) < 0) return false;
		ServerWorld world = (ServerWorld) bot.getEntityWorld();
		BlockPos spot = bestRailSpot(world);
		if (spot == null) return false;

		// rail, then cart: the same two clicks a player makes
		consume(Items.RAIL);
		world.setBlockState(spot, Blocks.RAIL.getDefaultState());
		world.playSound(null, spot, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 1, 1);
		ItemStack cartStack = new ItemStack(Items.TNT_MINECART);
		consume(Items.TNT_MINECART);
		cart = AbstractMinecartEntity.create(world, spot.getX() + 0.5, spot.getY() + 0.0625, spot.getZ() + 0.5, EntityType.TNT_MINECART, SpawnReason.DISPENSER, cartStack, bot);
		if (cart == null) return false;
		world.spawnEntity(cart);
		bot.swingHand(Hand.MAIN_HAND);
		bot.stopMoving();

		firePos = null;
		boolean useCrossbow = crossbow && hasLoadedCrossbow() && lightFire(world, spot);
		if (useCrossbow) {
			selectLoadedCrossbow();
			stepTicks = -2 - skill.reactionTicks() / 2; // a beat to line the shot up through the fire
		} else {
			bot.select(Items.BOW);
			bot.use(Hand.MAIN_HAND);
			stepTicks = 0;
		}
		step = Step.DRAW;
		return true;
	}

	private void tickCart() {
		stepTicks++;
		bot.inForward = 0;
		bot.inStrafe = 0;
		bot.setSprinting(false);
		switch (step) {
			case DRAW -> {
				if (cart == null || cart.isRemoved()) {
					finishCart();
					return;
				}
				bot.lookAt(cart.getEntityPos().add(0, 0.35, 0), 180);
				// back-pedal while lining the shot up: every block of distance is blast damage saved
				if (bot.getEntityPos().distanceTo(cart.getEntityPos()) < 5.5 && arena.distanceToEdge(bot.getEntityPos()) > 2) bot.inForward = -1;
				if (bot.holding(Items.CROSSBOW)) {
					if (stepTicks >= 0) {
						bot.use(Hand.MAIN_HAND); // a loaded crossbow fires the moment it is used
						step = Step.WAIT;
						stepTicks = 0;
					}
					return;
				}
				// Bow: better players use a short draw, it is faster and a point-blank cart needs no more.
				int draw = 6 + Math.round((1 - skill.t) * 12);
				if (stepTicks >= draw) {
					bot.stopUsingItem();
					step = Step.WAIT;
					stepTicks = 0;
				}
			}
			case WAIT -> {
				if (cart == null || cart.isRemoved() || stepTicks > 25) finishCart();
			}
			case RELOAD -> {
				faceTarget();
				bot.inForward = distanceToTarget() < 6 ? -1 : 0;
				stayInArena();
				if (stepTicks >= 28) {
					bot.stopUsingItem();
					step = Step.NONE;
				}
			}
			default -> finishCart();
		}
	}

	private void finishCart() {
		// A cart that never went off is a gift to the opponent: take it back.
		if (cart != null && !cart.isRemoved() && !cart.isPrimed()) cart.discard();
		if (firePos != null && bot.getEntityWorld().getBlockState(firePos).isOf(Blocks.FIRE)) bot.getEntityWorld().removeBlock(firePos, false);
		cart = null;
		firePos = null;
		bot.clearActiveItem();
		step = Step.NONE;
		nextCartAt = age + 30 + Math.round((1 - skill.t) * 90) + random.nextInt(20);
	}

	/**
	 * A spot beside the opponent that takes a rail, is far enough from the bot to be safe, and
	 * that the bot can shoot without the opponent standing in the line of fire.
	 */
	private BlockPos bestRailSpot(ServerWorld world) {
		BlockPos feet = target.getBlockPos();
		Vec3d eye = bot.getEyePos();
		BlockPos best = null;
		double bestScore = -99;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = feet.add(dx, dy, dz);
					if (!world.isAir(pos) || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
					if (arena.distanceToEdge(Vec3d.ofCenter(pos)) < 1) continue;
					if (!world.getOtherEntities(null, new Box(pos)).isEmpty()) continue;
					Vec3d centre = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5);
					double toBot = eye.distanceTo(centre);
					if (toBot > 9 || toBot < 3.4) continue;
					if (target.getBoundingBox().expand(0.35).raycast(eye, centre).isPresent()) continue;
					if (!canSee(centre)) continue;
					double toTarget = target.getEntityPos().distanceTo(centre);
					double score = -toTarget * 2 + toBot * skill.selfDamageWeight();
					if (score > bestScore) {
						bestScore = score;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	/** Lights the block in front of the cart, on the bot's side, so the bolt crosses it. */
	private boolean lightFire(ServerWorld world, BlockPos rail) {
		Vec3d toBot = bot.getEntityPos().subtract(Vec3d.ofCenter(rail)).multiply(1, 0, 1);
		if (toBot.lengthSquared() < 0.01) return false;
		Vec3d dir = toBot.normalize();
		BlockPos pos = BlockPos.ofFloored(rail.getX() + 0.5 + dir.x * 1.2, rail.getY(), rail.getZ() + 0.5 + dir.z * 1.2);
		if (pos.equals(rail) || !world.isAir(pos) || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) return false;
		if (find(Items.FLINT_AND_STEEL) < 0) return false;
		world.setBlockState(pos, Blocks.FIRE.getDefaultState());
		world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1, 1);
		firePos = pos;
		return true;
	}

	/* --------------------------------------------------------------- crossbow */

	private boolean hasLoadedCrossbow() {
		for (int i = 0; i < 9; i++) {
			ItemStack s = bot.getInventory().getStack(i);
			if (s.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(s)) return true;
		}
		return false;
	}

	private void selectLoadedCrossbow() {
		for (int i = 0; i < 9; i++) {
			ItemStack s = bot.getInventory().getStack(i);
			if (s.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(s)) {
				bot.getInventory().setSelectedSlot(i);
				return;
			}
		}
	}

	private boolean needsReload() {
		return bot.hotbarSlot(Items.CROSSBOW) >= 0 && !hasLoadedCrossbow() && find(Items.ARROW) >= 0;
	}

	private void beginReload() {
		bot.select(Items.CROSSBOW);
		bot.use(Hand.MAIN_HAND);
		step = Step.RELOAD;
		stepTicks = 0;
	}

	/* -------------------------------------------------------------- inventory */

	private int find(Item item) {
		for (int i = 0; i < bot.getInventory().size(); i++) if (bot.getInventory().getStack(i).isOf(item)) return i;
		return -1;
	}

	private void consume(Item item) {
		int slot = find(item);
		if (slot >= 0) bot.getInventory().getStack(slot).decrement(1);
	}
}
