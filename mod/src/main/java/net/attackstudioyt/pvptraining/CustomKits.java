package net.attackstudioyt.pvptraining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Kits the trainee saved with /kit save. A kit is the whole inventory exactly as it was:
 * every main slot, armour and off hand, with enchantments and components intact. One kit
 * per gamemode can be active, and it replaces the standard kit whenever a drill or duel
 * in that mode hands out gear. Stored in ~/.pvptraining/kits.json.
 */
public final class CustomKits {
	private CustomKits() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final EquipmentSlot[] WORN = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND };

	private static JsonObject data;

	/** Accepts what players actually type: cpvp, crystal, mace, spear, elytra, ely... */
	public static String modeId(String typed) {
		return switch (typed.toLowerCase(Locale.ROOT)) {
			case "mace" -> "mace";
			case "cpvp", "crystal", "cp" -> "crystal";
			case "spear" -> "spear";
			case "elytra", "ely", "elytra_mace", "elytramace", "elymace" -> "elytra_mace";
			default -> null;
		};
	}

	public static List<String> modeWords() {
		return List.of("mace", "cpvp", "spear", "elytra");
	}

	private static Path file() {
		return Progress.home().resolve("kits.json");
	}

	private static synchronized JsonObject data() {
		if (data == null) {
			try {
				if (Files.exists(file())) data = GSON.fromJson(Files.readString(file()), JsonObject.class);
			} catch (Exception e) {
				PVPTraining.LOG.warn("kits.json could not be read, starting fresh", e);
			}
			if (data == null) data = new JsonObject();
			if (!data.has("kits")) data.add("kits", new JsonObject());
			if (!data.has("active")) data.add("active", new JsonObject());
		}
		return data;
	}

	private static synchronized void save() {
		try {
			Files.createDirectories(Progress.home());
			Files.writeString(file(), GSON.toJson(data()));
		} catch (Exception e) {
			PVPTraining.LOG.warn("Could not save kits", e);
		}
	}

	private static JsonObject kitsFor(String mode) {
		JsonObject kits = data().getAsJsonObject("kits");
		if (!kits.has(mode)) kits.add(mode, new JsonObject());
		return kits.getAsJsonObject(mode);
	}

	/* ------------------------------------------------------------------ public */

	public static synchronized int saveKit(ServerPlayerEntity player, String name, String mode) {
		DynamicOps<JsonElement> ops = player.getRegistryManager().getOps(JsonOps.INSTANCE);
		JsonObject kit = new JsonObject();
		int count = 0;
		for (int i = 0; i < 36; i++) count += put(kit, "slot" + i, player.getInventory().getStack(i), ops);
		for (EquipmentSlot slot : WORN) count += put(kit, slot.getName(), player.getEquippedStack(slot), ops);
		kitsFor(mode).add(name, kit);
		data().getAsJsonObject("active").addProperty(mode, name);
		save();
		return count;
	}

	private static int put(JsonObject kit, String key, ItemStack stack, DynamicOps<JsonElement> ops) {
		if (stack.isEmpty()) return 0;
		ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(json -> kit.add(key, json));
		return kit.has(key) ? 1 : 0;
	}

	/** Name of the kit that will be used for this mode, or null for the standard kit. */
	public static synchronized String active(String mode) {
		JsonObject active = data().getAsJsonObject("active");
		if (!active.has(mode)) return null;
		String name = active.get(mode).getAsString();
		return kitsFor(mode).has(name) ? name : null;
	}

	public static synchronized boolean setActive(String mode, String name) {
		if (name == null) {
			data().getAsJsonObject("active").remove(mode);
			save();
			return true;
		}
		if (!kitsFor(mode).has(name)) return false;
		data().getAsJsonObject("active").addProperty(mode, name);
		save();
		return true;
	}

	public static synchronized boolean delete(String mode, String name) {
		if (kitsFor(mode).remove(name) == null) return false;
		if (name.equals(active(mode))) data().getAsJsonObject("active").remove(mode);
		save();
		return true;
	}

	public static synchronized List<String> names(String mode) {
		return new ArrayList<>(kitsFor(mode).keySet());
	}

	/** Finds which mode a kit name belongs to (first match), for commands that only take a name. */
	public static synchronized String modeOf(String name) {
		for (Map.Entry<String, JsonElement> e : data().getAsJsonObject("kits").entrySet()) {
			if (e.getValue().getAsJsonObject().has(name)) return e.getKey();
		}
		return null;
	}

	/** Puts a saved kit on the player. Returns false if there is no such kit. */
	public static synchronized boolean apply(ServerPlayerEntity player, String mode, String name) {
		JsonObject kits = kitsFor(mode);
		if (name == null || !kits.has(name)) return false;
		JsonObject kit = kits.getAsJsonObject(name);
		DynamicOps<JsonElement> ops = player.getRegistryManager().getOps(JsonOps.INSTANCE);
		player.getInventory().clear();
		for (int i = 0; i < 36; i++) {
			ItemStack stack = read(kit, "slot" + i, ops);
			if (!stack.isEmpty()) player.getInventory().setStack(i, stack);
		}
		for (EquipmentSlot slot : WORN) player.equipStack(slot, read(kit, slot.getName(), ops));
		return true;
	}

	private static ItemStack read(JsonObject kit, String key, DynamicOps<JsonElement> ops) {
		if (!kit.has(key)) return ItemStack.EMPTY;
		return ItemStack.CODEC.parse(ops, kit.get(key)).result().orElse(ItemStack.EMPTY);
	}
}
