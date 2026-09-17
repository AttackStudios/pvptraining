package net.attackstudioyt.pvptraining.bot;

import java.util.ArrayDeque;
import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;

/** Shared senses and motor skills. Subclasses decide what to do with them. */
public abstract class BotBrain {
	protected final BotPlayer bot;
	protected final Arena arena;
	protected final Skill skill;
	protected final Random random = Random.create();
	protected LivingEntity target;
	protected int age;
	/** While true the brain keeps the bot still (round countdowns). */
	public boolean frozen;

	private final ArrayDeque<Vec3d> targetHistory = new ArrayDeque<>();
	private Vec3d aimNoise = Vec3d.ZERO;
	private int strafeDir = 1;
	private int strafeTicks;
	private int eatTicks;

	protected BotBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target) {
		this.bot = bot;
		this.arena = arena;
		this.skill = skill;
		this.target = target;
	}

	public final void tick() {
		age++;
		if (target != null) {
			targetHistory.addLast(target.getEntityPos());
			while (targetHistory.size() > 14) targetHistory.removeFirst();
		}
		if (age % 7 == 0) {
			double spread = skill.aimErrorDegrees() / 22.0;
			aimNoise = new Vec3d(random.nextGaussian() * spread, random.nextGaussian() * spread * 0.6, random.nextGaussian() * spread);
		}
		if (frozen) {
			bot.stopMoving();
			if (target != null) bot.lookAt(target.getEyePos(), 20);
			return;
		}
		think();
	}

	protected abstract void think();

	public void onKilled(DamageSource source) {}

	/** Called by the session at the start of every round. */
	public void reset() {
		targetHistory.clear();
		eatTicks = 0;
		bot.stopMoving();
		bot.clearActiveItem();
	}

	/* ------------------------------------------------------------------ senses */

	/** Where the bot believes the target is: delayed by its reaction time, with aim error. */
	protected Vec3d perceivedTarget() {
		if (target == null) return bot.getEntityPos();
		int delay = Math.min(skill.reactionTicks(), targetHistory.size() - 1);
		Vec3d seen = target.getEntityPos();
		int i = targetHistory.size() - 1 - Math.max(delay, 0);
		int n = 0;
		for (Vec3d p : targetHistory) {
			if (n++ == i) {
				seen = p;
				break;
			}
		}
		return seen;
	}

	protected Vec3d aimPoint() {
		double dist = distanceToTarget();
		return perceivedTarget().add(0, target.getStandingEyeHeight() * 0.85, 0).add(aimNoise.multiply(Math.max(1.0, dist)));
	}

	protected double distanceToTarget() {
		return target == null ? 99 : bot.getEntityPos().distanceTo(target.getEntityPos());
	}

	protected double horizontalDistance(Vec3d to) {
		double dx = to.x - bot.getX();
		double dz = to.z - bot.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	protected boolean canSee(Vec3d point) {
		HitResult hit = bot.getEntityWorld().raycast(new RaycastContext(bot.getEyePos(), point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, bot));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** True when the target is inside melee reach and roughly under the crosshair. */
	protected boolean inReach(double reach) {
		if (target == null) return false;
		Vec3d eye = bot.getEyePos();
		Vec3d closest = new Vec3d(
			MathHelper.clamp(eye.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
			MathHelper.clamp(eye.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
			MathHelper.clamp(eye.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
		if (eye.distanceTo(closest) > reach) return false;
		Vec3d toTarget = closest.subtract(eye).normalize();
		return bot.getRotationVec(1.0F).dotProduct(toTarget) > 0.93 || eye.distanceTo(closest) < 1.0;
	}

	/* ------------------------------------------------------------------- motor */

	protected float faceTarget() {
		return bot.lookAt(aimPoint(), skill.turnSpeed());
	}

	/** Walks where the bot is looking, circling the target once close. */
	protected void approach(double holdDistance, boolean sprint) {
		double dist = distanceToTarget();
		if (--strafeTicks <= 0) {
			strafeDir = random.nextBoolean() ? 1 : -1;
			strafeTicks = 12 + random.nextInt(28);
		}
		if (dist > holdDistance + 0.6) {
			bot.inForward = 1;
			bot.inStrafe = dist < 7 ? strafeDir * skill.strafeAmount() * 0.6F : 0;
			bot.setSprinting(sprint);
		} else if (dist < holdDistance - 0.9) {
			bot.inForward = -0.8F;
			bot.inStrafe = strafeDir * skill.strafeAmount();
			bot.setSprinting(false);
		} else {
			bot.inForward = 0.25F;
			bot.inStrafe = strafeDir * skill.strafeAmount();
			bot.setSprinting(false);
		}
		// Blasts leave craters and ledges: hop out of them instead of walking into the wall.
		bot.inJump = bot.horizontalCollision && bot.isOnGround();
		stayInArena();
	}

	/** Overrides the inputs with a push back toward the middle when the bot drifts to the wall. */
	protected void stayInArena() {
		if (arena.distanceToEdge(bot.getEntityPos()) > 3.5) return;
		Vec3d home = arena.center();
		float yawToHome = (float) (MathHelper.atan2(home.z - bot.getZ(), home.x - bot.getX()) * 180.0 / Math.PI) - 90.0F;
		float diff = MathHelper.wrapDegrees(yawToHome - bot.getYaw());
		// Translate "toward home" into forward/strafe relative to where the bot is facing.
		double rad = Math.toRadians(diff);
		bot.inForward = (float) Math.cos(rad);
		bot.inStrafe = (float) -Math.sin(rad);
	}

	/** Backs off and eats a golden apple. Returns true while it owns the tick. */
	protected boolean eatIfNeeded() {
		boolean hungry = bot.getHealth() + bot.getAbsorptionAmount() <= skill.eatBelow();
		if (eatTicks == 0 && (!hungry || bot.hotbarSlot(Items.GOLDEN_APPLE) < 0)) return false;
		if (eatTicks == 0) {
			bot.select(Items.GOLDEN_APPLE);
			bot.use(Hand.MAIN_HAND);
			eatTicks = 1;
		}
		eatTicks++;
		faceTarget();
		bot.inForward = distanceToTarget() < 6 ? -1 : 0;
		bot.inStrafe = strafeDir;
		bot.setSprinting(false);
		stayInArena();
		if (!bot.isUsingItem() || eatTicks > 40) {
			eatTicks = 0;
			bot.clearActiveItem();
		}
		return eatTicks != 0;
	}
}
