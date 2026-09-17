package net.attackstudioyt.pvptraining.bot;

/** Everything that separates a Rookie bot from an Ace, derived from one 1-10 difficulty. */
public final class Skill {
	public final int difficulty;
	/** 0 at difficulty 1, 1 at difficulty 10. */
	public final float t;

	public Skill(int difficulty) {
		this.difficulty = Math.max(1, Math.min(10, difficulty));
		this.t = (this.difficulty - 1) / 9.0F;
	}

	private float lerp(float rookie, float ace) {
		return rookie + (ace - rookie) * t;
	}

	/** Ticks of delay before the bot "sees" where you moved. */
	public int reactionTicks() { return Math.round(lerp(11, 1)); }
	public float aimErrorDegrees() { return lerp(9.0F, 0.5F); }
	public float turnSpeed() { return lerp(16, 70); }
	/** Chance that a melee hit is set up as a jump crit. */
	public float critChance() { return lerp(0.1F, 0.95F); }
	/** Chance of swinging early and landing a weak hit. */
	public float earlySwingChance() { return lerp(0.35F, 0.0F); }
	public float strafeAmount() { return lerp(0.25F, 1.0F); }
	/** Health at or below which the bot backs off to eat. */
	public float eatBelow() { return lerp(5, 11); }
	public int diveCooldown() { return Math.round(lerp(170, 40)); }
	public int lungeCooldown() { return Math.round(lerp(70, 7)); }
	public int elytraCooldown() { return Math.round(lerp(320, 130)); }
	public int elytraClimb() { return Math.round(lerp(14, 34)); }
	public float shieldChance() { return lerp(0.0F, 0.8F); }
	public int crystalPlaceDelay() { return Math.round(lerp(16, 2)); }
	public int crystalBreakDelay() { return Math.round(lerp(10, 1)); }
	public int retotemDelay() { return Math.round(lerp(26, 2)); }
	/** How much of its own blast damage the bot is willing to ignore. */
	public float selfDamageWeight() { return lerp(0.25F, 0.8F); }
}
