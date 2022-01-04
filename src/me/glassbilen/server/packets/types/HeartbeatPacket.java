package me.glassbilen.server.packets.types;

import me.glassbilen.network.CNetworkManager;
import me.glassbilen.network.Packet;
import me.glassbilen.server.users.UserSocket;

public class HeartbeatPacket implements Packet {

	@Override
	public String getPacketName() {
		return "Heartbeat";
	}

	@Override
	public int getArgsWanted() {
		return 0;
	}

	@Override
	public void onReceive(CNetworkManager manager, String[] args) {
		UserSocket user = (UserSocket) manager;

		if (!user.isAuthenticated()) {
			user.setAuthenticated(true);
			user.onAuthenticate();
		}

		user.updateLastHeartbeat();
	}
}
