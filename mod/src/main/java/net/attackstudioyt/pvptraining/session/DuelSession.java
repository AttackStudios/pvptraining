package net.attackstudioyt.pvptraining.session;

import java.util.Map;
import net.attackstudioyt.pvptraining.Catalog;
import net.attackstudioyt.pvptraining.Kits;
import net.attackstudioyt.pvptraining.bot.BotBrain;
import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.attackstudioyt.pvptraining.bot.CartBrain;
import net.attackstudioyt.pvptraining.bot.CrystalBrain;
import net.attackstudioyt.pvptraining.bot.MaceBrain;
import net.attackstudioyt.pvptraining.bot.Skill;
import net.attackstudioyt.pvptraining.bot.SwordBrain;
import net.attackstudioyt.pvptraining.world.ArenaBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** First to three rounds against a bot of the chosen tier, same kit on both sides. */
public class DuelSession extends Session {
	private static final int ROUNDS_TO_WIN = 3;

	private final Catalog.Tier tier;
	private BotPlayer bot;
	private int playerRounds;
	private int botRounds;
	private int pause;
	private float damageDealt;

	public DuelSession(ServerPlayerEntity player, String modeId, Catalog.Tier tier) {
		super(player, modeId);
		this.tier = tier;
	}

	@Override
	public String name() {
		return "Duel: " + tier.name + " bot";
	}

	@Override
	public void start() {
		bot = spawnBot(tier.name);
		Skill skill = new Skill(tier.difficulty);
		BotBrain brain = switch (modeId) {
			case "crystal" -> new CrystalBrain(bot, arena, skill, player);
			case "sword" -> new SwordBrain(bot, arena, skill, player);
			case "cart" -> new CartBrain(bot, arena, skill, player, false);
			case "xbow" -> new CartBrain(bot, arena, skill, player, true);
			case "spear" -> new MaceBrain(bot, arena, skill, player, MaceBrain.Variant.SPEAR);
			case "elytra_mace" -> new MaceBrain(bot, arena, skill, player, MaceBrain.Variant.ELYTRA);
			default -> new MaceBrain(bot, arena, skill, player, MaceBrain.Variant.MACE);
		};
		bot.brain = brain;
		title(Text.literal(tier.name + " bot").formatted(Formatting.GOLD, Formatting.BOLD), Text.literal("First to " + ROUNDS_TO_WIN), 50);
		newRound();
	}

	private void newRound() {
		ArenaBuilder.reset(world, arena);
		Kits.give(player, modeId);
		Kits.give(bot, modeId);
		toSpawn();
		placeBot(bot);
		bot.brain.reset();
		beginCountdown();
	}

	@Override
	protected void live() {
		setBar("You " + playerRounds + "  ·  " + botRounds + " " + tier.name, bot.getHealth() / bot.getMaxHealth(), BossBar.Color.RED);
		if (pause > 0 && --pause == 0) {
			if (playerRounds >= ROUNDS_TO_WIN || botRounds >= ROUNDS_TO_WIN) finish();
			else newRound();
		}
	}

	@Override
	public void onPlayerHit(LivingEntity victim, DamageSource source, float taken) {
		if (victim == bot) damageDealt += taken;
	}

	@Override
	public void onPlayerDown(DamageSource source) {
		if (pause > 0 || countdown > 0) return;
		botRounds++;
		say("Round to the " + tier.name + " bot. " + playerRounds + " - " + botRounds, "bad");
		cue(SoundEvents.ENTITY_WITHER_HURT, 0.5F, 0.7F);
		roundOver();
	}

	@Override
	public void onBotDown(BotPlayer down, DamageSource source) {
		down.setHealth(down.getMaxHealth());
		if (pause > 0 || countdown > 0) return;
		playerRounds++;
		say("Round to you. " + playerRounds + " - " + botRounds, "good");
		cue(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.9F, 1.2F);
		roundOver();
	}

	private void roundOver() {
		pause = 50;
		bot.brain.frozen = true;
		bot.brain.reset();
		player.setHealth(player.getMaxHealth());
	}

	@Override
	protected Map<String, String> stats() {
		return map("You", String.valueOf(playerRounds), tier.name, String.valueOf(botRounds),
			"Bot health", fmt(Math.round(bot.getHealth())), "Damage dealt", fmt(Math.round(damageDealt)));
	}

	private void finish() {
		SessionManager.reportDuel(this, modeId, tier, playerRounds > botRounds, playerRounds, botRounds);
		end();
	}
}
