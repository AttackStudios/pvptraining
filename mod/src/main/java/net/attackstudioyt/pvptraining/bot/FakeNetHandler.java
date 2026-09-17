package net.attackstudioyt.pvptraining.bot;

import java.util.Set;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;

public class FakeNetHandler extends ServerPlayNetworkHandler {
	public FakeNetHandler(MinecraftServer server, ClientConnection connection, BotPlayer player, ConnectedClientData data) {
		super(server, connection, player, data);
	}

	// Bots are only ever removed by the session that owns them.
	@Override public void disconnect(Text reason) {}

	@Override
	public void requestTeleport(EntityPosition pos, Set<PositionFlag> flags) {
		super.requestTeleport(pos, flags);
		if (player.getEntityWorld().getPlayerByUuid(player.getUuid()) != null) {
			syncWithPlayerPosition();
			player.getEntityWorld().getChunkManager().updatePosition(player);
		}
	}
}
