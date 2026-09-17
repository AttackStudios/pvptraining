package net.attackstudioyt.pvptraining.bot;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.NetworkState;
import org.jspecify.annotations.Nullable;

/** A connection with nothing on the other end: every packet sent to a bot is dropped. */
public class FakeClientConnection extends ClientConnection {
	public FakeClientConnection() {
		super(NetworkSide.SERVERBOUND);
	}

	@Override public boolean isOpen() { return true; }
	@Override public void tryDisableAutoRead() {}
	@Override public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}
	@Override public void flush() {}
	@Override public void handleDisconnection() {}
	// Vanilla would touch the (null) netty channel here because isOpen() says true.
	@Override public void disconnect(DisconnectionInfo info) {}
	@Override public void setInitialPacketListener(PacketListener listener) {}
	@Override public <T extends PacketListener> void transitionInbound(NetworkState<T> state, T listener) {}
	@Override public void transitionOutbound(NetworkState<?> state) {}
}
