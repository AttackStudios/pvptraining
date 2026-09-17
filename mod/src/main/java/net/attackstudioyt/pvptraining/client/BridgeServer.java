package net.attackstudioyt.pvptraining.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.attackstudioyt.pvptraining.PVPTraining;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * The local socket the desktop app talks to. Loopback only, and every connection has
 * to present the per-launch token from the instance file, so a web page or another
 * user on the machine cannot drive the game.
 */
public class BridgeServer extends WebSocketServer {
	private static final int FIRST_PORT = 47811;
	private static final int PORT_SPAN = 24;

	private final String token;
	private final Consumer<JsonObject> handler;
	private final Set<WebSocket> trusted = ConcurrentHashMap.newKeySet();

	private BridgeServer(int port, String token, Consumer<JsonObject> handler) {
		super(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
		this.token = token;
		this.handler = handler;
		setReuseAddr(true);
		setDaemon(true);
	}

	public static BridgeServer open(Consumer<JsonObject> handler) throws Exception {
		byte[] raw = new byte[24];
		new SecureRandom().nextBytes(raw);
		BridgeServer server = new BridgeServer(freePort(), HexFormat.of().formatHex(raw), handler);
		server.start();
		return server;
	}

	private static int freePort() throws Exception {
		for (int port = FIRST_PORT; port < FIRST_PORT + PORT_SPAN; port++) {
			try (ServerSocket probe = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
				return probe.getLocalPort();
			} catch (Exception busy) {
				// another Minecraft instance has this one
			}
		}
		try (ServerSocket any = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			return any.getLocalPort();
		}
	}

	public String token() {
		return token;
	}

	public boolean hasClients() {
		return !trusted.isEmpty();
	}

	public void broadcast(JsonObject msg) {
		String text = msg.toString();
		for (WebSocket ws : trusted) if (ws.isOpen()) ws.send(text);
	}

	public void shutdown() {
		try {
			stop(500);
		} catch (Exception ignored) {
			// closing anyway
		}
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		String origin = handshake.getFieldValue("Origin");
		boolean browser = origin != null && (origin.startsWith("http://") || origin.startsWith("https://"));
		if (browser || !conn.getRemoteSocketAddress().getAddress().isLoopbackAddress()) conn.close(1008, "not allowed");
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		JsonObject msg;
		try {
			msg = JsonParser.parseString(message).getAsJsonObject();
		} catch (Exception e) {
			return;
		}
		if (!trusted.contains(conn)) {
			boolean ok = msg.has("token") && java.security.MessageDigest.isEqual(token.getBytes(), msg.get("token").getAsString().getBytes());
			if (!ok) {
				JsonObject denied = new JsonObject();
				denied.addProperty("t", "denied");
				denied.addProperty("reason", "This game did not recognise the app. Restart both and try again.");
				conn.send(denied.toString());
				conn.close(1008, "bad token");
				return;
			}
			trusted.add(conn);
		}
		try {
			handler.accept(msg);
		} catch (Exception e) {
			PVPTraining.LOG.warn("Bridge message failed: {}", message, e);
		}
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		trusted.remove(conn);
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		PVPTraining.LOG.warn("Bridge socket error", ex);
	}

	@Override
	public void onStart() {}
}
