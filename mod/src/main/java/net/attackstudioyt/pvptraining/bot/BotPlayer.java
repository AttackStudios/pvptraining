package net.attackstudioyt.pvptraining.bot;

import com.mojang.authlib.GameProfile;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.play.PlayerLoadedC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * A server-side player with no client behind it. Vanilla only moves players in
 * response to their own packets, so the bot drives itself: inputs are written by
 * a {@link BotBrain} and applied here every tick.
 */
public class BotPlayer extends ServerPlayerEntity {
	public float inForward;
	public float inStrafe;
	public boolean inJump;
	public boolean inSneak;
	public BotBrain brain;

	private final MinecraftServer srv;
	private boolean removedByUs;
	private Vec3d knockback;

	private BotPlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
		super(server, world, profile, SyncedClientOptions.createDefault());
		this.srv = server;
	}

	/** Server thread only. */
	public static BotPlayer spawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos, float yaw) {
		GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid("pvpt:" + name), name);
		ServerPlayerEntity stale = server.getPlayerManager().getPlayer(profile.id());
		if (stale instanceof BotPlayer old) old.remove();

		BotPlayer bot = new BotPlayer(server, world, profile);
		bot.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
		FakeClientConnection connection = new FakeClientConnection();
		ConnectedClientData data = new ConnectedClientData(profile, 0, bot.getClientOptions(), false);
		server.getPlayerManager().onPlayerConnect(connection, bot, data);
		new FakeNetHandler(server, connection, bot, data);
		// Without this the bot sits in a 60 tick "still loading" window where it cannot be hurt.
		bot.networkHandler.onPlayerLoaded(new PlayerLoadedC2SPacket());
		bot.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, 0, true);
		bot.setHealth(20.0F);
		bot.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(0.6);
		bot.interactionManager.changeGameMode(GameMode.SURVIVAL);
		bot.dataTracker.set(PLAYER_MODE_CUSTOMIZATION_ID, (byte) 0x7f);
		server.getPlayerManager().sendToDimension(new EntitySetHeadYawS2CPacket(bot, (byte) (bot.headYaw * 256 / 360)), world.getRegistryKey());
		server.getPlayerManager().sendToDimension(EntityPositionSyncS2CPacket.create(bot), world.getRegistryKey());
		return bot;
	}

	public void remove() {
		if (removedByUs) return;
		removedByUs = true;
		brain = null;
		getInventory().clear();
		networkHandler.onDisconnected(new DisconnectionInfo(Text.literal("Training finished")));
	}

	public boolean isGone() {
		return removedByUs || isRemoved();
	}

	@Override
	public void tick() {
		if (removedByUs) return;
		if (srv.getTicks() % 10 == 0) {
			networkHandler.syncWithPlayerPosition();
			getEntityWorld().getChunkManager().updatePosition(this);
		}
		if (knockback != null) {
			setVelocity(knockback);
			knockback = null;
		}
		if (brain != null) brain.tick();
		setSneaking(inSneak);
		float scale = inSneak ? 0.3F : 1.0F;
		forwardSpeed = inForward * scale;
		sidewaysSpeed = inStrafe * scale;
		setJumping(inJump);
		Vec3d before = getEntityPos();
		super.tick();
		playerTick();
		// Entity.move only tracks falls for the side that owns the movement, which is never
		// the server for a player. Without this the bot could not crit, smash or take fall damage.
		Vec3d moved = getEntityPos().subtract(before);
		handleFall(moved.x, moved.y, moved.z, isOnGround());
	}

	/* ---------------------------------------------------------------- controls */

	public void stopMoving() {
		inForward = 0;
		inStrafe = 0;
		inJump = false;
		inSneak = false;
		setSprinting(false);
	}

	public void look(float yaw, float pitch) {
		setYaw(MathHelper.wrapDegrees(yaw));
		setPitch(MathHelper.clamp(pitch, -90, 90));
		setHeadYaw(getYaw());
	}

	/** Turns toward a point, limited to maxTurn degrees this tick. Returns the remaining yaw error. */
	public float lookAt(Vec3d target, float maxTurn) {
		Vec3d eye = getEyePos();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		float wantPitch = (float) -(MathHelper.atan2(dy, horizontal) * 180.0 / Math.PI);
		float dYaw = MathHelper.wrapDegrees(wantYaw - getYaw());
		float dPitch = wantPitch - getPitch();
		look(getYaw() + MathHelper.clamp(dYaw, -maxTurn, maxTurn), getPitch() + MathHelper.clamp(dPitch, -maxTurn, maxTurn));
		return Math.abs(MathHelper.wrapDegrees(wantYaw - getYaw()));
	}

	public void hit(Entity target) {
		attack(target);
		swingHand(Hand.MAIN_HAND);
		resetTicksSince();
		updateLastActionTime();
	}

	public boolean use(Hand hand) {
		updateLastActionTime();
		return interactionManager.interactItem(this, getEntityWorld(), getStackInHand(hand), hand).isAccepted();
	}

	/** Jab with a spear, exactly what vanilla does for the STAB player action. */
	public boolean stab() {
		ItemStack stack = getMainHandStack();
		PiercingWeaponComponent piercing = stack.get(DataComponentTypes.PIERCING_WEAPON);
		if (piercing == null) return false;
		piercing.stab(this, EquipmentSlot.MAINHAND);
		resetTicksSince();
		updateLastActionTime();
		return true;
	}

	/** Hotbar slot holding the item, or -1. */
	public int hotbarSlot(Item item) {
		for (int i = 0; i < 9; i++) if (getInventory().getStack(i).isOf(item)) return i;
		return -1;
	}

	public boolean select(Item item) {
		int slot = hotbarSlot(item);
		if (slot < 0) return false;
		getInventory().setSelectedSlot(slot);
		return true;
	}

	public boolean holding(Item item) {
		return getMainHandStack().isOf(item);
	}

	/** Swaps whatever is in the chest slot with the first inventory stack of the given item. */
	public boolean wearFromInventory(Item item) {
		if (getEquippedStack(EquipmentSlot.CHEST).isOf(item)) return true;
		for (int i = 0; i < getInventory().size(); i++) {
			ItemStack stack = getInventory().getStack(i);
			if (!stack.isOf(item)) continue;
			ItemStack worn = getEquippedStack(EquipmentSlot.CHEST).copy();
			equipStack(EquipmentSlot.CHEST, stack.copy());
			getInventory().setStack(i, worn);
			return true;
		}
		return false;
	}

	/* --------------------------------------------------------------- knockback */

	/**
	 * When a player hits another player, vanilla ships the knockback to the victim's client and
	 * then puts the victim's server-side velocity back, because a real client moves itself. A bot
	 * has no client, so every hit would be swallowed. Remember the knockback here and re-apply it
	 * at the start of the next tick, after that rollback. Deliberately not a mixin: other bot mods
	 * (HeroBot, Carpet) already redirect that spot in PlayerEntity and two redirects cannot coexist.
	 */
	@Override
	public void takeKnockback(double strength, double x, double z) {
		super.takeKnockback(strength, x, z);
		knockback = getVelocity();
	}

	/* ------------------------------------------------------------------- death */

	// Sessions end rounds before a bot can really die; this is the safety net so a
	// stray death never drops a kit on the floor or kicks the fake connection.
	@Override
	public void onDeath(DamageSource source) {
		setHealth(getMaxHealth());
		if (brain != null) brain.onKilled(source);
	}

	@Override public String getIp() { return "127.0.0.1"; }
	@Override public boolean allowsServerListing() { return false; }
}
