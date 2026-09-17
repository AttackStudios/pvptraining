package net.attackstudioyt.pvptraining.client;

import com.google.gson.JsonObject;
import net.attackstudioyt.pvptraining.Live;
import net.attackstudioyt.pvptraining.PVPTraining;
import net.attackstudioyt.pvptraining.Progress;
import net.attackstudioyt.pvptraining.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Client half of the mod: hosts the local bridge the desktop app connects to. */
public class PVPTrainingClient implements ClientModInitializer {
	private static BridgeServer bridge;
	private static int tick;
	private static String lastState = "";

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			try {
				bridge = BridgeServer.open(PVPTrainingClient::handle);
				Discovery.publish(bridge.getPort(), bridge.token(), client.getSession().getUsername(), modVersion());
				PVPTraining.LOG.info("PVPTraining bridge listening on 127.0.0.1:{}", bridge.getPort());
			} catch (Exception e) {
				PVPTraining.LOG.error("Could not start the PVPTraining bridge. The app will not be able to connect.", e);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			Discovery.remove();
			if (bridge != null) bridge.shutdown();
		});
		ClientTickEvents.END_CLIENT_TICK.register(PVPTrainingClient::tick);
	}

	static String modVersion() {
		return FabricLoader.getInstance().getModContainer(PVPTraining.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	private static void tick(MinecraftClient client) {
		if (bridge == null || !bridge.hasClients()) {
			Live.OUTBOX.clear();
			return;
		}
		JsonObject out;
		while ((out = Live.OUTBOX.poll()) != null) bridge.broadcast(out);
		if (++tick % 5 != 0) return;
		JsonObject state = state(client, "state");
		String encoded = state.toString();
		if (!encoded.equals(lastState) || tick % 40 == 0) {
			lastState = encoded;
			bridge.broadcast(state);
		}
	}

	static JsonObject state(MinecraftClient client, String type) {
		JsonObject s = new JsonObject();
		s.addProperty("t", type);
		s.addProperty("player", client.getSession().getUsername());
		s.addProperty("mcVersion", PVPTraining.MC_VERSION);
		s.addProperty("modVersion", modVersion());
		s.addProperty("practice", client.isIntegratedServerRunning() && Live.practiceWorld && client.player != null);
		s.addProperty("inWorld", client.world != null);
		JsonObject session = Live.session;
		if (session != null) s.add("session", session);
		return s;
	}

	/** Messages from the app. Arrives on the websocket thread, so hop to the right thread first. */
	private static void handle(JsonObject msg) {
		MinecraftClient client = MinecraftClient.getInstance();
		String type = msg.has("t") ? msg.get("t").getAsString() : "";
		switch (type) {
			case "hello" -> {
				JsonObject welcome = state(client, "welcome");
				bridge.broadcast(welcome);
				JsonObject progress = new JsonObject();
				progress.addProperty("t", "progress");
				progress.add("progress", Progress.get().toJson());
				bridge.broadcast(progress);
			}
			case "enterWorld" -> client.execute(() -> WorldJoiner.enter(client));
			case "start" -> {
				String mode = msg.get("mode").getAsString();
				String activity = msg.get("activity").getAsString();
				String id = msg.get("id").getAsString();
				onServer(client, (server, player) -> {
					String err = SessionManager.start(player, mode, activity, id);
					if (err != null) Live.error(err);
				});
			}
			case "stop" -> onServer(client, (server, player) -> SessionManager.stop());
			default -> { }
		}
	}

	private interface ServerAction {
		void run(IntegratedServer server, ServerPlayerEntity player);
	}

	private static void onServer(MinecraftClient client, ServerAction action) {
		client.execute(() -> {
			IntegratedServer server = client.getServer();
			if (server == null || client.player == null || !Live.practiceWorld) {
				Live.error("You are not in the practice world yet.");
				return;
			}
			java.util.UUID uuid = client.player.getUuid();
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
				if (player != null) action.run(server, player);
			});
		});
	}
}
