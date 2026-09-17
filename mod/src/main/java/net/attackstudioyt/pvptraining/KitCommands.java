package net.attackstudioyt.pvptraining;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.attackstudioyt.pvptraining.session.SessionManager;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /kit save &lt;name&gt; &lt;mace|cpvp|spear|elytra&gt; and friends. Build the layout you like in the
 * hub (start from /kit edit), save it, and every drill and duel in that mode uses it.
 */
public final class KitCommands {
	private KitCommands() {}

	private static final SuggestionProvider<ServerCommandSource> MODES = (ctx, builder) -> CommandSource.suggestMatching(CustomKits.modeWords(), builder);
	private static final SuggestionProvider<ServerCommandSource> NAMES = (ctx, builder) -> {
		List<String> all = new ArrayList<>();
		for (String word : CustomKits.modeWords()) all.addAll(CustomKits.names(CustomKits.modeId(word)));
		return CommandSource.suggestMatching(all, builder);
	};

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("kit")
			.then(CommandManager.literal("save")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.then(CommandManager.argument("mode", StringArgumentType.word()).suggests(MODES).executes(KitCommands::save))))
			.then(CommandManager.literal("edit")
				.then(CommandManager.argument("mode", StringArgumentType.word()).suggests(MODES).executes(KitCommands::edit)))
			.then(CommandManager.literal("load")
				.then(CommandManager.argument("name", StringArgumentType.word()).suggests(NAMES).executes(KitCommands::load)))
			.then(CommandManager.literal("use")
				.then(CommandManager.argument("name", StringArgumentType.word()).suggests(NAMES).executes(KitCommands::use)))
			.then(CommandManager.literal("default")
				.then(CommandManager.argument("mode", StringArgumentType.word()).suggests(MODES).executes(KitCommands::useDefault)))
			.then(CommandManager.literal("delete")
				.then(CommandManager.argument("name", StringArgumentType.word()).suggests(NAMES).executes(KitCommands::delete)))
			.then(CommandManager.literal("list").executes(KitCommands::list)));
	}

	/* ---------------------------------------------------------------- helpers */

	private static ServerPlayerEntity trainee(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		if (!Live.practiceWorld) throw fail("Kits only work in the PVPTraining world. Press Connect in the app first.");
		return player;
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Text.literal(message)).create();
	}

	private static String mode(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		String typed = StringArgumentType.getString(ctx, "mode");
		String mode = CustomKits.modeId(typed);
		if (mode == null) throw fail("Unknown gamemode \"" + typed + "\". Use mace, cpvp, spear or elytra.");
		return mode;
	}

	private static String modeName(String mode) {
		Catalog.Mode m = Catalog.get().mode(mode);
		return m == null ? mode : m.name;
	}

	private static void ok(CommandContext<ServerCommandSource> ctx, String text) {
		ctx.getSource().sendFeedback(() -> Text.literal("Kits: ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(Formatting.GRAY)), false);
	}

	private static void noSession() throws CommandSyntaxException {
		if (SessionManager.current() != null) throw fail("Finish or stop the running session first (/pvpt stop).");
	}

	/* --------------------------------------------------------------- commands */

	private static int save(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = trainee(ctx);
		String mode = mode(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		int items = CustomKits.saveKit(player, name, mode);
		if (items == 0) {
			CustomKits.delete(mode, name);
			throw fail("Your inventory is empty. Use /kit edit " + StringArgumentType.getString(ctx, "mode") + " to start from the standard kit.");
		}
		ok(ctx, "saved \"" + name + "\" (" + items + " stacks). It is now your " + modeName(mode) + " kit for every drill and duel.");
		return 1;
	}

	private static int edit(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = trainee(ctx);
		noSession();
		String mode = mode(ctx);
		Kits.give(player, mode, false);
		ok(ctx, "here is the standard " + modeName(mode) + " kit. Rearrange it, then /kit save <name> " + StringArgumentType.getString(ctx, "mode") + ".");
		return 1;
	}

	private static int load(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = trainee(ctx);
		noSession();
		String name = StringArgumentType.getString(ctx, "name");
		String mode = CustomKits.modeOf(name);
		if (mode == null || !CustomKits.apply(player, mode, name)) throw fail("No kit called \"" + name + "\". See /kit list.");
		player.playerScreenHandler.syncState();
		ok(ctx, "loaded \"" + name + "\" (" + modeName(mode) + ").");
		return 1;
	}

	private static int use(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		trainee(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		String mode = CustomKits.modeOf(name);
		if (mode == null || !CustomKits.setActive(mode, name)) throw fail("No kit called \"" + name + "\". See /kit list.");
		ok(ctx, "\"" + name + "\" is now your " + modeName(mode) + " kit.");
		return 1;
	}

	private static int useDefault(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		trainee(ctx);
		String mode = mode(ctx);
		CustomKits.setActive(mode, null);
		ok(ctx, modeName(mode) + " is back on the standard kit. Your saved kits are still there.");
		return 1;
	}

	private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		trainee(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		String mode = CustomKits.modeOf(name);
		if (mode == null || !CustomKits.delete(mode, name)) throw fail("No kit called \"" + name + "\".");
		ok(ctx, "deleted \"" + name + "\".");
		return 1;
	}

	private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		trainee(ctx);
		boolean any = false;
		for (String word : CustomKits.modeWords()) {
			String mode = CustomKits.modeId(word);
			List<String> names = CustomKits.names(mode);
			if (names.isEmpty()) continue;
			any = true;
			String active = CustomKits.active(mode);
			StringBuilder line = new StringBuilder(modeName(mode)).append(": ");
			for (int i = 0; i < names.size(); i++) {
				if (i > 0) line.append(", ");
				line.append(names.get(i));
				if (names.get(i).equals(active)) line.append(" (in use)");
			}
			ok(ctx, line.toString());
		}
		if (!any) ok(ctx, "no saved kits yet. Try /kit edit mace, rearrange, then /kit save main mace.");
		return 1;
	}
}
