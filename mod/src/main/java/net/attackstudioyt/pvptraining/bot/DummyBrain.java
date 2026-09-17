package net.attackstudioyt.pvptraining.bot;

import net.attackstudioyt.pvptraining.world.Arena;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A target for drills. It never fights back: it stands, or wanders like a player dodging. */
public class DummyBrain extends BotBrain {
	public enum Style { STILL, WANDER }

	private final Style style;
	private final boolean holdsTotem;
	private Vec3d waypoint;
	private int waypointTicks;
	private int retotemIn = -1;
	/** Totems popped since the brain was created. */
	public int pops;

	public DummyBrain(BotPlayer bot, Arena arena, LivingEntity target, Style style, boolean holdsTotem) {
		super(bot, arena, new Skill(5), target);
		this.style = style;
		this.holdsTotem = holdsTotem;
		if (holdsTotem) bot.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
	}

	@Override
	protected void think() {
		// Drill dummies are punching bags: keep them topped up so every hit is measured the same way.
		// The totem dummy is the exception: it has to be able to drop low enough to pop.
		if (holdsTotem) tickTotem();
		else if (bot.getHealth() < bot.getMaxHealth() && bot.hurtTime == 0) bot.setHealth(bot.getMaxHealth());

		if (style == Style.STILL) {
			bot.stopMoving();
			if (target != null) bot.lookAt(target.getEyePos(), 12);
			// drift back to the spot if knocked away
			Vec3d home = arena.botSpawn();
			if (horizontalDistance(home) > 1.5 && bot.isOnGround()) walkTo(home, false);
			return;
		}

		if (waypoint == null || --waypointTicks <= 0 || horizontalDistance(waypoint) < 1.2) {
			waypoint = arena.randomPoint(random, 6);
			waypointTicks = 40 + random.nextInt(60);
		}
		walkTo(waypoint, true);
		bot.inJump = bot.isOnGround() && random.nextInt(34) == 0;
	}

	private void walkTo(Vec3d point, boolean sprint) {
		float yaw = (float) (MathHelper.atan2(point.z - bot.getZ(), point.x - bot.getX()) * 180.0 / Math.PI) - 90.0F;
		float diff = MathHelper.wrapDegrees(yaw - bot.getYaw());
		bot.look(bot.getYaw() + MathHelper.clamp(diff, -18, 18), 0);
		bot.inForward = 1;
		bot.inStrafe = 0;
		bot.setSprinting(sprint);
	}

	private void tickTotem() {
		boolean has = bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
		if (!has && retotemIn < 0) {
			pops++;
			retotemIn = 6;
		}
		if (retotemIn >= 0 && --retotemIn < 0) bot.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
	}
}
