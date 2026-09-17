package net.attackstudioyt.pvptraining;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The shared list of modes, drills, medal targets and bot tiers (same file the app reads). */
public final class Catalog {
	public List<Tier> tiers;
	public Mastery mastery;
	public List<Mode> modes;

	public static final class Tier {
		public String id;
		public String name;
		public int difficulty;
	}

	public static final class Mastery {
		public int unlockAt;
		public int drillWeight;
		public int duelWeight;
		public String requiredTier;
	}

	public static final class Mode {
		public String id;
		public String name;
		public String kind;
		public String parent;
		public List<Drill> drills;
	}

	public static final class Drill {
		public String id;
		public String name;
		public String goal;
		public String unit;
		public boolean lowerIsBetter;
		public double[] medals;
		public int seconds;
		public int attempts;

		public int medalFor(double score) {
			int medal = 0;
			for (int i = 0; i < medals.length; i++) {
				if (lowerIsBetter ? (score > 0 && score <= medals[i]) : score >= medals[i]) medal = i + 1;
			}
			return medal;
		}
	}

	private static Catalog instance;

	public static Catalog get() {
		if (instance == null) {
			try (InputStream in = Catalog.class.getResourceAsStream("/pvptraining/catalog.json")) {
				instance = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
			} catch (Exception e) {
				throw new IllegalStateException("catalog.json is missing from the mod", e);
			}
		}
		return instance;
	}

	public Mode mode(String id) {
		for (Mode m : modes) if (m.id.equals(id)) return m;
		return null;
	}

	public Drill drill(String modeId, String drillId) {
		Mode m = mode(modeId);
		if (m == null) return null;
		for (Drill d : m.drills) if (d.id.equals(drillId)) return d;
		return null;
	}

	public Tier tier(String id) {
		for (Tier t : tiers) if (t.id.equals(id)) return t;
		return null;
	}

	public int tierIndex(String id) {
		for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).id.equals(id)) return i;
		return -1;
	}
}
