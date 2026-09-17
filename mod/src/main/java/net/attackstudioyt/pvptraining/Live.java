package net.attackstudioyt.pvptraining;

import com.google.gson.JsonObject;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Hand-off point between the integrated server thread (where sessions run) and the
 * client thread (where the app bridge lives). Both sides only ever swap whole objects.
 */
public final class Live {
	private Live() {}

	/** Snapshot of the running session, or null. Replaced, never mutated. */
	public static volatile JsonObject session;
	/** True while the integrated server is running the practice world. */
	public static volatile boolean practiceWorld;
	/** Messages waiting to be pushed to the desktop app. */
	public static final ConcurrentLinkedQueue<JsonObject> OUTBOX = new ConcurrentLinkedQueue<>();

	public static void event(String text, String tone) {
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "event");
		msg.addProperty("text", text);
		msg.addProperty("tone", tone);
		OUTBOX.add(msg);
	}

	public static void error(String message) {
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "error");
		msg.addProperty("message", message);
		OUTBOX.add(msg);
	}
}
