package me.glassbilen.server.packets.types;

import java.io.IOException;

import me.glassbilen.io.text.TextColor;
import me.glassbilen.network.CNetworkManager;
import me.glassbilen.network.Packet;
import me.glassbilen.server.users.UserSocket;

public class LogErrorPacket implements Packet {

	@Override
	public String getPacketName() {
		return "LogError";
	}

	@Override
	public int getArgsWanted() {
		return 1;
	}

	@Override
	public void onReceive(CNetworkManager manager, String[] args) {
		UserSocket user = (UserSocket) manager;
		user.getOutput().println(TextColor.RED + "[CLIENT ERROR] " + args[0].replace("\r", "\\r").replace("\n", "\\n"));

		try {
			user.sendLine("LogConfirmed");
		} catch (IOException e) {
			e.printStackTrace();
		}

		user.close();
	}
}
