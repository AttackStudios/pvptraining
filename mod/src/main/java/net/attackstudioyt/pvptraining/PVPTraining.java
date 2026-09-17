package net.attackstudioyt.pvptraining;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.attackstudioyt.pvptraining.session.DrillSession;
import net.attackstudioyt.pvptraining.session.Session;
import net.attackstudioyt.pvptraining.session.SessionManager;
import net.attackstudioyt.pvptraining.world.Arena;
import net.attackstudioyt.pvptraining.world.ArenaBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PVPTraining implements ModInitializer {
	public static final String MOD_ID = "pvptraining";
	public static final String WORLD_NAME = "PVPTraining";
	public static final String MC_VERSION = "1.21.11";
	public static final Logger LOG = LoggerFactory.getLogger("PVPTraining");
	/** Start the game with -Dpvptraining.debug=true to log every hit in the practice world. */
	public static final boolean DEBUG = Boolean.getBoolean("pvptraining.debug");

	@Override
	public void onInitialize() {
		Catalog.get();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Live.practiceWorld = isPractice(server);
			if (Live.practiceWorld) prepareWorld(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SessionManager.stop();
			Live.practiceWorld = false;
			Live.session = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (Live.practiceWorld) SessionManager.tick();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!Live.practiceWorld || handler.player instanceof BotPlayer) return;
			ServerPlayerEntity player = handler.player;
			player.changeGameMode(GameMode.SURVIVAL);
			player.getInventory().clear();
			Vec3d hub = Arena.HUB.center();
			player.teleport(server.getOverworld(), hub.x, hub.y, hub.z, Set.of(), 0, 0, true);
			player.sendMessage(Text.literal("PVPTraining: ").formatted(Formatting.GOLD).append(Text.literal("pick a drill or a duel in the app, or use /pvpt.").formatted(Formatting.GRAY)), false);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (!(handler.player instanceof BotPlayer)) SessionManager.stop();
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Session s = SessionManager.current();
			if (DEBUG && Live.practiceWorld) {
				LOG.info("[dmg] {} took {} ({} raw) from {} by {}{}", entity.getName().getString(), taken, base, source.getName(),
					source.getAttacker() == null ? "-" : source.getAttacker().getName().getString(), blocked ? " [blocked]" : "");
			}
			if (s == null || entity == sessionPlayer(s)) return;
			boolean byTrainee = source.getAttacker() == sessionPlayer(s);
			// In a drill only the trainee sets anything off, and a minecart blast does not always
			// carry their name, so any explosion that reaches the dummy is theirs.
			boolean drillBlast = s instanceof DrillSession && entity instanceof BotPlayer && source.isIn(DamageTypeTags.IS_EXPLOSION);
			if (byTrainee || drillBlast) s.onPlayerHit(entity, source, taken);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(PVPTraining::allowDeath);

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> KitCommands.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
			CommandManager.literal("pvpt")
				.then(CommandManager.literal("start")
					.then(CommandManager.argument("mode", StringArgumentType.word())
						.then(CommandManager.argument("activity", StringArgumentType.word())
							.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
								if (!Live.practiceWorld) {
									ctx.getSource().sendError(Text.literal("Open the PVPTraining world first (press Connect in the app)."));
									return 0;
								}
								String err = SessionManager.start(p, StringArgumentType.getString(ctx, "mode"), StringArgumentType.getString(ctx, "activity"), StringArgumentType.getString(ctx, "id"));
								if (err != null) ctx.getSource().sendError(Text.literal(err));
								return err == null ? 1 : 0;
							})))))
				.then(CommandManager.literal("stop").executes(ctx -> {
					SessionManager.stop();
					return 1;
				}))));
	}

	private static ServerPlayerEntity sessionPlayer(Session s) {
		return s.trainee();
	}

	public static boolean isPractice(MinecraftServer server) {
		return WORLD_NAME.equals(server.getSaveProperties().getLevelName());
	}

	private static void prepareWorld(MinecraftServer server) {
		GameRules rules = server.getOverworld().getGameRules();
		rules.setValue(GameRules.ADVANCE_TIME, false, server);
		rules.setValue(GameRules.ADVANCE_WEATHER, false, server);
		rules.setValue(GameRules.DO_MOB_SPAWNING, false, server);
		rules.setValue(GameRules.KEEP_INVENTORY, true, server);
		rules.setValue(GameRules.DO_IMMEDIATE_RESPAWN, true, server);
		rules.setValue(GameRules.ANNOUNCE_ADVANCEMENTS, false, server);
		rules.setValue(GameRules.SHOW_DEATH_MESSAGES, false, server);
		rules.setValue(GameRules.DO_TILE_DROPS, false, server);
		rules.setValue(GameRules.SPAWN_PHANTOMS, false, server);
		server.getOverworld().setTimeOfDay(6000);
		ArenaBuilder.ensureBuilt(server);
	}

	/** Nobody really dies in the practice world: a fatal blow ends the round instead. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!Live.practiceWorld || !(entity instanceof ServerPlayerEntity player)) return true;
		// This event fires before vanilla checks for a totem, so let a held totem do its job.
		if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && holdsTotem(player)) return true;

		player.setHealth(player.getMaxHealth());
		player.setFireTicks(0);
		player.fallDistance = 0;
		Session s = SessionManager.current();
		if (s != null && s.owns(player)) {
			if (player instanceof BotPlayer bot) s.onBotDown(bot, source);
			else s.onPlayerDown(source);
		} else if (!(player instanceof BotPlayer)) {
			Vec3d hub = Arena.HUB.center();
			player.teleport(player.getEntityWorld().getServer().getOverworld(), hub.x, hub.y, hub.z, Set.of(), 0, 0, true);
		}
		return false;
	}

	private static boolean holdsTotem(ServerPlayerEntity player) {
		return player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
	}

	/** Called from the stab mixin whenever any player jabs with a spear. */
	public static void onStab(ServerPlayerEntity player, ItemStack spear) {
		Session s = SessionManager.current();
		if (s == null || s.trainee() != player) return;
		var lunge = player.getEntityWorld().getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getOptional(Enchantments.LUNGE);
		if (lunge.isEmpty() || EnchantmentHelper.getLevel(lunge.get(), spear) <= 0) return;
		// Same conditions vanilla applies before the Lunge impulse fires.
		if (player.isGliding() || player.isTouchingWater() || player.hasVehicle()) return;
		s.onLunge();
	}
}
