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
		// Single-player pauses when the window loses focus, which is exactly when the app is in front.
		s.addProperty("paused", client.isPaused());
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
			// Proves to the PVPTraining friends service that this really is the account it says it is,
			// the same way joining a multiplayer server does: we tell Mojang "I am joining <nonce>",
			// and the service asks Mojang whether that happened. The access token goes to Mojang only;
			// the app and the friends service never see it.
			case "socialAuth" -> {
				String nonce = msg.get("nonce").getAsString();
				Thread worker = new Thread(() -> {
					JsonObject reply = new JsonObject();
					reply.addProperty("t", "socialAuthed");
					reply.addProperty("nonce", nonce);
					reply.addProperty("name", client.getSession().getUsername());
					try {
						java.util.UUID uuid = client.getSession().getUuidOrNull();
						if (uuid == null) throw new IllegalStateException("This game is not signed in to a Minecraft account.");
						client.getApiServices().sessionService().joinServer(uuid, client.getSession().getAccessToken(), nonce);
						reply.addProperty("ok", true);
					} catch (Exception e) {
						reply.addProperty("ok", false);
						reply.addProperty("error", "Minecraft could not confirm your account. Make sure the game is signed in, then try again.");
						PVPTraining.LOG.warn("Social sign-in failed: {}", e.toString());
					}
					bridge.broadcast(reply);
				}, "pvptraining-social-auth");
				worker.setDaemon(true);
				worker.start();
			}
			// Test hook (-Dpvptraining.debug=true only): the trainee punches the nearest bot so an
			// automated run can check that bots really get knocked back.
			// Test hook (debug only): run a command as the trainee, so automated runs can drive /kit.
			case "debugCommand" -> {
				String command = msg.get("command").getAsString();
				if (PVPTraining.DEBUG) onServer(client, (server, player) -> {
					server.getCommandManager().parseAndExecute(player.getCommandSource(), command);
					PVPTraining.LOG.info("[debugCommand] /{} -> main hand {}, slot0 {}, offhand {}", command,
						player.getMainHandStack().getItem(), player.getInventory().getStack(0).getItem(), player.getOffHandStack().getItem());
				});
			}
			case "debugHit" -> {
				if (PVPTraining.DEBUG) onServer(client, (server, player) -> {
					for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
						if (!(other instanceof net.attackstudioyt.pvptraining.bot.BotPlayer bot)) continue;
						player.teleport((net.minecraft.server.world.ServerWorld) bot.getEntityWorld(), bot.getX() - 2, bot.getY(), bot.getZ(), java.util.Set.of(), -90, 0, true);
						net.minecraft.util.math.Vec3d before = bot.getEntityPos();
						player.setSprinting(true);
						player.attack(bot);
						java.util.concurrent.CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> server.execute(() ->
							PVPTraining.LOG.info("[debugHit] {} moved {} blocks after the hit", bot.getName().getString(), String.format("%.2f", bot.getEntityPos().distanceTo(before)))));
						return;
					}
				});
			}
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
