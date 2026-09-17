package net.attackstudioyt.pvptraining.bot;

import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;

/**
 * Pure sword fighting, the way it is actually played:
 * <ul>
 * <li><b>W-tap</b>: let go of forward for a moment after each hit so the next one is a fresh
 *     sprint hit and carries full knockback.</li>
 * <li><b>S-tap</b>: step back after a hit so the opponent's swing falls short, then step in again.</li>
 * <li><b>Combo</b>: a knocked-up opponent cannot hit back; stay glued underneath and keep landing hits.</li>
 * <li><b>Crits</b>: swing on the way down from a jump.</li>
 * <li><b>Strafe</b>: circle so you are harder to aim at.</li>
 * </ul>
 * How often and how cleanly it does each is set by {@link Skill}.
 */
public class SwordBrain extends BotBrain {
	private int wTapTicks;
	private int sTapTicks;
	private int critTicks = -1;
	private int comboHits;

	public SwordBrain(BotPlayer bot, Arena arena, Skill skill, LivingEntity target) {
		super(bot, arena, skill, target);
	}

	@Override
	public void reset() {
		super.reset();
		wTapTicks = 0;
		sTapTicks = 0;
		critTicks = -1;
		comboHits = 0;
	}

	@Override
	protected void think() {
		if (target == null || !target.isAlive()) {
			bot.stopMoving();
			return;
		}
		if (bot.isOnGround() && distanceToTarget() > 6 && eatIfNeeded()) return;

		bot.select(sword());
		faceTarget();

		boolean targetAirborne = !target.isOnGround() && target.hurtTime > 0;
		// During a combo close right in; otherwise fight from the edge of reach.
		approach(targetAirborne ? 2.0 : 2.7, true);

		if (wTapTicks > 0) {
			// W-tap: forward released, sprint dropped. It re-sprints on the next approach().
			wTapTicks--;
			bot.inForward = 0;
			bot.setSprinting(false);
		}
		if (sTapTicks > 0) {
			sTapTicks--;
			bot.inForward = -1;
			bot.setSprinting(false);
		}

		float charge = bot.getAttackCooldownProgress(0.5F);
		boolean ready = charge >= 0.95F || (charge > 0.5F && random.nextFloat() < skill.earlySwingChance() * 0.1F);
		double dist = distanceToTarget();

		if (critTicks < 0 && bot.isOnGround() && !targetAirborne && dist < 3.6 && charge > 0.55F && charge < 0.9F && random.nextFloat() < skill.critChance() * 0.25F) {
			bot.inJump = true;
			critTicks = 0;
			return;
		}
		if (critTicks >= 0) {
			critTicks++;
			bot.inJump = false;
			boolean falling = bot.getVelocity().y < -0.08 && !bot.isOnGround();
			if (falling && ready && inReach(3.0)) {
				bot.setSprinting(false); // a sprinting hit cannot crit
				land();
				critTicks = -1;
			} else if (critTicks > 16 || (bot.isOnGround() && critTicks > 3)) critTicks = -1;
			return;
		}

		if (ready && inReach(3.0) && canSee(target.getEyePos())) land();
		if (target.isOnGround() && target.hurtTime == 0) comboHits = 0;
	}

	private net.minecraft.item.Item sword() {
		if (bot.hotbarSlot(Items.NETHERITE_SWORD) >= 0) return Items.NETHERITE_SWORD;
		return Items.DIAMOND_SWORD;
	}

	private void land() {
		bot.hit(target);
		comboHits++;
		// Better players reset their sprint after every hit; weaker ones only sometimes.
		if (random.nextFloat() < 0.25F + skill.t * 0.75F) wTapTicks = 2;
		// S-tap mostly when the opponent is grounded and able to trade back.
		if (target.isOnGround() && random.nextFloat() < skill.t * 0.6F) sTapTicks = 3 + random.nextInt(3);
	}
}
