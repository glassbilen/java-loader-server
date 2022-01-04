package me.glassbilen.server.packets;

import me.glassbilen.network.CPacketManager;
import me.glassbilen.server.packets.types.HeartbeatPacket;
import me.glassbilen.server.packets.types.LogErrorPacket;
import me.glassbilen.server.packets.types.RequestClassPacket;
import me.glassbilen.server.packets.types.RequestLibrary;
import me.glassbilen.server.packets.types.RequestProgramPacket;
import me.glassbilen.server.packets.types.RequestResourcePacket;

public class PacketManager extends CPacketManager {
	public PacketManager() {
		addPacket(new HeartbeatPacket());
		addPacket(new LogErrorPacket());
		addPacket(new RequestClassPacket());
		addPacket(new RequestLibrary());
		addPacket(new RequestProgramPacket());
		addPacket(new RequestResourcePacket());
	}
}
