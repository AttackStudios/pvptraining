package net.attackstudioyt.pvptraining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the trainee has earned. Lives in ~/.pvptraining/progress.json so the
 * desktop app can show the skill tree even when the game is closed.
 */
public final class Progress {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int schema = 1;
	public Map<String, ModeProgress> modes = new LinkedHashMap<>();
	public List<Entry> history = new ArrayList<>();

	public static final class ModeProgress {
		public double mastery;
		public boolean unlocked;
		public Map<String, DrillProgress> drills = new LinkedHashMap<>();
		public Map<String, DuelProgress> duels = new LinkedHashMap<>();
	}

	public static final class DrillProgress {
		public double best;
		public int medal;
		public int runs;
	}

	public static final class DuelProgress {
		public int matchWins;
		public int matchLosses;
	}

	public static final class Entry {
		public long ts;
		public String mode;
		public String name;
		public String result;
	}

	private static Progress instance;

	public static Path home() {
		return Path.of(System.getProperty("user.home"), ".pvptraining");
	}

	public static synchronized Progress get() {
		if (instance == null) {
			Path file = home().resolve("progress.json");
			try {
				if (Files.exists(file)) instance = GSON.fromJson(Files.readString(file), Progress.class);
			} catch (Exception e) {
				PVPTraining.LOG.warn("progress.json could not be read, starting fresh", e);
			}
			if (instance == null) instance = new Progress();
			instance.recompute();
		}
		return instance;
	}

	public synchronized void save() {
		try {
			Files.createDirectories(home());
			Path tmp = home().resolve("progress.json.tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, home().resolve("progress.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			PVPTraining.LOG.warn("Could not save progress", e);
		}
	}

	public synchronized JsonElement toJson() {
		return GSON.toJsonTree(this);
	}

	public synchronized ModeProgress mode(String id) {
		return modes.computeIfAbsent(id, k -> new ModeProgress());
	}

	public synchronized boolean isUnlocked(String modeId) {
		Catalog.Mode mode = Catalog.get().mode(modeId);
		return mode != null && (mode.parent == null || mode(modeId).unlocked);
	}

	/** Records a drill run. Returns the medal earned for this run. */
	public synchronized int recordDrill(String modeId, Catalog.Drill drill, double score) {
		DrillProgress p = mode(modeId).drills.computeIfAbsent(drill.id, k -> new DrillProgress());
		p.runs++;
		int medal = drill.medalFor(score);
		boolean better = p.runs == 1 || (drill.lowerIsBetter ? (score > 0 && (p.best <= 0 || score < p.best)) : score > p.best);
		if (better) p.best = score;
		if (medal > p.medal) p.medal = medal;
		return medal;
	}

	public synchronized void recordDuel(String modeId, String tierId, boolean won) {
		DuelProgress p = mode(modeId).duels.computeIfAbsent(tierId, k -> new DuelProgress());
		if (won) p.matchWins++;
		else p.matchLosses++;
	}

	public synchronized void log(String modeId, String name, String result) {
		Entry e = new Entry();
		e.ts = System.currentTimeMillis();
		e.mode = modeId;
		e.name = name;
		e.result = result;
		history.add(e);
		while (history.size() > 60) history.remove(0);
	}

	private int bestTierIndex(ModeProgress p) {
		int best = -1;
		Catalog c = Catalog.get();
		for (int i = 0; i < c.tiers.size(); i++) {
			DuelProgress d = p.duels.get(c.tiers.get(i).id);
			if (d != null && d.matchWins > 0) best = i;
		}
		return best;
	}

	/** Recalculates mastery and unlocks. Returns the ids of branches that just unlocked. */
	public synchronized List<String> recompute() {
		Catalog c = Catalog.get();
		List<String> unlockedNow = new ArrayList<>();
		for (Catalog.Mode mode : c.modes) {
			ModeProgress p = mode(mode.id);
			double medals = 0;
			for (Catalog.Drill d : mode.drills) {
				DrillProgress dp = p.drills.get(d.id);
				if (dp != null) medals += dp.medal / 3.0;
			}
			double drillPart = mode.drills.isEmpty() ? 0 : medals / mode.drills.size() * c.mastery.drillWeight;
			double duelPart = (bestTierIndex(p) + 1) / (double) c.tiers.size() * c.mastery.duelWeight;
			p.mastery = Math.round((drillPart + duelPart) * 10) / 10.0;
			if (mode.parent == null) p.unlocked = true;
		}
		for (Catalog.Mode mode : c.modes) {
			if (mode.parent == null) continue;
			ModeProgress self = mode(mode.id);
			if (self.unlocked) continue;
			ModeProgress parent = mode(mode.parent);
			if (parent.mastery >= c.mastery.unlockAt && bestTierIndex(parent) >= c.tierIndex(c.mastery.requiredTier)) {
				self.unlocked = true;
				unlockedNow.add(mode.id);
			}
		}
		return unlockedNow;
	}
}
