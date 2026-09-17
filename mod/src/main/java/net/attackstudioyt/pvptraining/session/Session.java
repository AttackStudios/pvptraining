package net.attackstudioyt.pvptraining.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.attackstudioyt.pvptraining.Kits;
import net.attackstudioyt.pvptraining.Live;
import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.attackstudioyt.pvptraining.world.Arena;
import net.attackstudioyt.pvptraining.world.ArenaBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** One drill or duel, from countdown to result. Runs entirely on the server thread. */
public abstract class Session {
	protected final ServerPlayerEntity player;
	protected final ServerWorld world;
	protected final String modeId;
	protected final Arena arena;
	protected final ServerBossBar bar;
	protected final List<BotPlayer> bots = new ArrayList<>();
	protected int ticks;
	/** Ticks left in the 3-2-1 before play starts; 0 once live. */
	protected int countdown;
	private boolean finished;

	protected Session(ServerPlayerEntity player, String modeId) {
		this.player = player;
		this.world = (ServerWorld) player.getEntityWorld();
		this.modeId = modeId;
		this.arena = arenaFor(modeId);
		this.bar = new ServerBossBar(Text.literal(""), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
		this.bar.addPlayer(player);
	}

	public static Arena arenaFor(String modeId) {
		return switch (modeId) {
			case "crystal" -> Arena.CRYSTAL;
			case "sword" -> Arena.SWORD;
			case "cart", "xbow" -> Arena.CART;
			case "spear" -> Arena.SPEAR;
			case "elytra_mace" -> Arena.ELYTRA;
			default -> Arena.MACE;
		};
	}

	public ServerPlayerEntity trainee() {
		return player;
	}

	public abstract String name();

	public abstract void start();

	/** Called every tick once the countdown is over. */
	protected abstract void live();

	/** Label to value, in display order, for the app's live panel. */
	protected abstract Map<String, String> stats();

	public void onPlayerHit(LivingEntity victim, DamageSource source, float taken) {}

	/** The trainee took fatal damage with no totem left. They have already been healed. */
	public void onPlayerDown(DamageSource source) {}

	public void onBotDown(BotPlayer bot, DamageSource source) {}

	public void onLunge() {}

	public final void tick() {
		if (finished) return;
		ticks++;
		Kits.feed(player);
		if (player.getY() < arena.y - 24) toSpawn();
		if (countdown > 0) {
			if (countdown % 20 == 0) {
				int n = countdown / 20;
				title(Text.literal(String.valueOf(n)).formatted(Formatting.GOLD), null, 12);
				sound(n);
			}
			countdown--;
			if (countdown == 0) {
				title(Text.literal("GO").formatted(Formatting.GREEN, Formatting.BOLD), null, 14);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1, 2);
				for (BotPlayer bot : bots) if (bot.brain != null) bot.brain.frozen = false;
			}
		} else live();
		// live() may have just ended the session: never publish a finished one, or the app
		// would believe it is still running and refuse to start the next drill.
		if (!finished && ticks % 5 == 0) publish();
	}

	private void sound(int n) {
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1, 1.0F + (3 - n) * 0.15F);
	}

	/* ---------------------------------------------------------------- helpers */

	protected void beginCountdown() {
		countdown = 60;
		for (BotPlayer bot : bots) if (bot.brain != null) bot.brain.frozen = true;
	}

	protected void toSpawn() {
		Vec3d p = arena.playerSpawn();
		player.teleport(world, p.x, p.y, p.z, Set.of(), -90, 0, true);
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0;
		if (player.isGliding()) player.stopGliding();
	}

	protected BotPlayer spawnBot(String botName) {
		BotPlayer bot = BotPlayer.spawn(world.getServer(), world, botName, arena.botSpawn(), 90);
		Kits.give(bot, modeId);
		bots.add(bot);
		return bot;
	}

	protected void placeBot(BotPlayer bot) {
		Vec3d p = arena.botSpawn();
		bot.teleport(world, p.x, p.y, p.z, Set.of(), 90, 0, true);
		bot.setVelocity(Vec3d.ZERO);
		bot.fallDistance = 0;
		if (bot.isGliding()) bot.stopGliding();
	}

	protected void title(Text title, Text subtitle, int stay) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(2, stay, 6));
		if (subtitle != null) player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		player.networkHandler.sendPacket(new TitleS2CPacket(title));
	}

	protected void say(String text, String tone) {
		Formatting colour = "good".equals(tone) ? Formatting.GREEN : "bad".equals(tone) ? Formatting.RED : Formatting.GRAY;
		player.sendMessage(Text.literal(text).formatted(colour), true);
		if ("bad".equals(tone)) cue(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F, 0.6F);
		Live.event(text, tone);
	}

	/** Plays a sound only the trainee hears, at their position. */
	protected void cue(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
		world.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	protected void setBar(String text, float progress, BossBar.Color color) {
		bar.setName(Text.literal(text));
		bar.setPercent(Math.max(0, Math.min(1, progress)));
		bar.setColor(color);
	}

	public boolean owns(LivingEntity entity) {
		return entity == player || (entity instanceof BotPlayer b && bots.contains(b));
	}

	protected static String fmt(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}

	protected static Map<String, String> map(String... pairs) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
		return m;
	}

	/** Pushes the current numbers to the app. Also called once at start so the app reacts before the first tick. */
	void publish() {
		if (finished) return;
		JsonObject s = new JsonObject();
		s.addProperty("mode", modeId);
		s.addProperty("name", name());
		JsonArray arr = new JsonArray();
		stats().forEach((label, value) -> {
			JsonObject st = new JsonObject();
			st.addProperty("label", label);
			st.addProperty("value", value);
			arr.add(st);
		});
		s.add("stats", arr);
		Live.session = s;
	}

	/* ------------------------------------------------------------------ ending */

	public boolean isFinished() {
		return finished;
	}

	/** Tears the session down. Subclasses report their result before calling this. */
	public void end() {
		if (finished) return;
		finished = true;
		bar.clearPlayers();
		for (BotPlayer bot : bots) bot.remove();
		bots.clear();
		ArenaBuilder.reset(world, arena);
		Live.session = null;
		if (!player.isDisconnected()) {
			player.getInventory().clear();
			player.clearStatusEffects();
			player.setHealth(player.getMaxHealth());
			if (player.isGliding()) player.stopGliding();
			Vec3d hub = Arena.HUB.center();
			player.teleport(world, hub.x, hub.y, hub.z, Set.of(), 0, 0, true);
		}
	}
}
