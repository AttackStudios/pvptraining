package net.attackstudioyt.pvptraining.client;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import net.attackstudioyt.pvptraining.PVPTraining;
import net.attackstudioyt.pvptraining.Progress;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Drops ~/.pvptraining/instances/&lt;pid&gt;.json so the desktop app can find this game,
 * whichever launcher started it and whatever port the bridge ended up on.
 */
final class Discovery {
	private Discovery() {}

	private static Path file;

	static void publish(int port, String token, String username, String modVersion) {
		try {
			Path dir = Progress.home().resolve("instances");
			Files.createDirectories(dir);
			long pid = ProcessHandle.current().pid();
			file = dir.resolve(pid + ".json");
			JsonObject info = new JsonObject();
			info.addProperty("pid", pid);
			info.addProperty("port", port);
			info.addProperty("token", token);
			info.addProperty("username", username);
			info.addProperty("mcVersion", PVPTraining.MC_VERSION);
			info.addProperty("modVersion", modVersion);
			info.addProperty("launcher", guessLauncher());
			info.addProperty("gameDir", FabricLoader.getInstance().getGameDir().toAbsolutePath().toString());
			info.addProperty("startedAt", System.currentTimeMillis());
			Files.writeString(file, info.toString());
			try {
				Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException windows) {
				// NTFS: the user profile folder is already private
			}
			Runtime.getRuntime().addShutdownHook(new Thread(Discovery::remove, "pvptraining-cleanup"));
		} catch (Exception e) {
			PVPTraining.LOG.error("Could not publish the PVPTraining instance file", e);
		}
	}

	static void remove() {
		try {
			if (file != null) Files.deleteIfExists(file);
		} catch (Exception ignored) {
			// the app also prunes files whose pid is gone
		}
	}

	private static String guessLauncher() {
		String dir = FabricLoader.getInstance().getGameDir().toAbsolutePath().toString().toLowerCase(Locale.ROOT);
		String brand = String.valueOf(System.getProperty("minecraft.launcher.brand", "")).toLowerCase(Locale.ROOT);
		String all = dir + " " + brand + " " + String.valueOf(System.getProperty("java.class.path", "")).toLowerCase(Locale.ROOT);
		if (all.contains("lunarclient") || all.contains("lunar")) return "Lunar Client";
		if (all.contains("modrinth")) return "Modrinth App";
		if (all.contains("dawn") || all.contains("feather")) return "Dawn";
		if (all.contains("prism")) return "Prism Launcher";
		if (all.contains("curseforge")) return "CurseForge";
		return "Minecraft Launcher";
	}
}
