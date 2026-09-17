package net.attackstudioyt.pvptraining.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.attackstudioyt.pvptraining.Catalog;
import net.attackstudioyt.pvptraining.Live;
import net.attackstudioyt.pvptraining.Progress;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Owns the one running session. Server thread only. */
public final class SessionManager {
	private SessionManager() {}

	private static Session current;

	public static Session current() {
		return current != null && !current.isFinished() ? current : null;
	}

	/** Returns null on success, or a message for the trainee. */
	public static String start(ServerPlayerEntity player, String modeId, String activity, String id) {
		Catalog catalog = Catalog.get();
		Catalog.Mode mode = catalog.mode(modeId);
		if (mode == null) return "Unknown gamemode: " + modeId;
		if (!Progress.get().isUnlocked(modeId)) return mode.name + " is still locked. Master " + catalog.mode(mode.parent).name + " first.";
		stop();
		if ("duel".equals(activity)) {
			Catalog.Tier tier = catalog.tier(id);
			if (tier == null) return "Unknown bot tier: " + id;
			current = new DuelSession(player, modeId, tier);
		} else {
			Catalog.Drill drill = catalog.drill(modeId, id);
			if (drill == null) return "Unknown drill: " + id;
			current = new DrillSession(player, modeId, drill);
		}
		current.start();
		current.publish();
		return null;
	}

	public static void stop() {
		if (current != null && !current.isFinished()) current.end();
		current = null;
		Live.session = null;
	}

	public static void tick() {
		if (current == null) return;
		if (current.isFinished() || current.player.isDisconnected()) {
			stop();
			return;
		}
		current.tick();
	}

	/* ----------------------------------------------------------------- results */

	static void reportDrill(Session s, String modeId, Catalog.Drill drill, double score) {
		Progress progress = Progress.get();
		int medal = progress.recordDrill(modeId, drill, score);
		String medalName = new String[] { "no medal", "Bronze", "Silver", "Gold" }[medal];
		String scoreText = drill.lowerIsBetter && score <= 0 ? "no time" : Session.fmt(score) + " " + drill.unit;
		progress.log(modeId, drill.name, scoreText + (medal > 0 ? " · " + medalName : ""));
		finishReport(s, progress, drill.name + ": " + scoreText + (medal > 0 ? ", " + medalName + " medal" : ""), medal, false);
		Formatting colour = medal == 3 ? Formatting.GOLD : medal == 2 ? Formatting.WHITE : medal == 1 ? Formatting.RED : Formatting.GRAY;
		s.title(Text.literal(medal > 0 ? medalName : "Finished").formatted(colour, Formatting.BOLD), Text.literal(scoreText), 60);
	}

	static void reportDuel(Session s, String modeId, Catalog.Tier tier, boolean won, int mine, int theirs) {
		Progress progress = Progress.get();
		progress.recordDuel(modeId, tier.id, won);
		String result = (won ? "Won " : "Lost ") + mine + "-" + theirs;
		progress.log(modeId, "Duel: " + tier.name + " bot", result);
		finishReport(s, progress, result + " against the " + tier.name + " bot", 0, won);
		s.title(Text.literal(won ? "Victory" : "Defeat").formatted(won ? Formatting.GREEN : Formatting.RED, Formatting.BOLD), Text.literal(mine + " - " + theirs), 60);
	}

	private static void finishReport(Session s, Progress progress, String summary, int medal, boolean won) {
		List<String> unlocked = progress.recompute();
		progress.save();
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "result");
		msg.addProperty("summary", summary);
		msg.addProperty("medal", medal);
		msg.addProperty("won", won);
		JsonArray arr = new JsonArray();
		unlocked.forEach(arr::add);
		msg.add("unlocked", arr);
		msg.add("progress", progress.toJson());
		Live.OUTBOX.add(msg);
		boolean good = medal > 0 || won;
		s.cue(good ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BLOCK_BEACON_DEACTIVATE, good ? 0.9F : 1.0F, good ? (medal == 3 ? 1.2F : 1.0F) : 0.8F);

		for (String id : unlocked) {
			String name = Catalog.get().mode(id).name;
			s.player.sendMessage(Text.literal("Skill branch unlocked: " + name).formatted(Formatting.AQUA, Formatting.BOLD), false);
			s.world.playSound(null, s.player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1, 1);
		}
	}
}
