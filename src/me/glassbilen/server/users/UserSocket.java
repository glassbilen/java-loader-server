package me.glassbilen.server.users;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

import me.glassbilen.common.security.SecuredApp;
import me.glassbilen.io.text.OutputHandler;
import me.glassbilen.io.text.TextColor;
import me.glassbilen.network.CNetworkManager;
import me.glassbilen.network.exceptions.MaliciousPacketException;
import me.glassbilen.network.exceptions.NoPacketHandlerException;
import me.glassbilen.server.logging.OutputHandlerIdentifier;
import me.glassbilen.server.packets.PacketManager;

public class UserSocket extends CNetworkManager {
	private OutputHandler output;
	private boolean closeOnStart;

	private SecuredApp app;
	private File loadedFile;

	public UserSocket(Socket socket, PacketManager packetManager, boolean closeOnStart) {
		super(socket, packetManager);

		this.closeOnStart = closeOnStart;

		output = new OutputHandlerIdentifier(true, getIdentifier());

		try {
			init(false);
			start();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		String encryptionKey = UUID.randomUUID().toString().replace("-", "");

		try {
			sendLine(encryptionKey);
			setEncryptionKey(encryptionKey);

			if (closeOnStart) {
				sendLine("Close", "Too many open connections.", "You have too many open connections, close them before starting the program or try again later.");
				close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onProcess(String input) {
		if (!isValidUser() || !isAccepted()) {
			output.println(TextColor.RED + "Packet rejected since it hasnt been verified yet[" + input + "].");
			return;
		}

		try {
			handleDefaultPacket(input);
		} catch (MaliciousPacketException e) {
			output.println(e.getMessage());
			e.printStackTrace();
		} catch (NoPacketHandlerException e) {
			output.println("Packet " + e.getMessage() + " has no handler.");
		}
	}

	// Delayed
	@Override
	public void onAuthenticate() {}

	@Override
	public void onClose() {}

	public String getIdentifier() {
		return getIp();
	}

	public boolean isValidUser() {
		return !closeOnStart;
	}

	public SecuredApp getSecuredApp() {
		return app;
	}

	public File getLoadedFile() {
		return loadedFile;
	}

	public void setSecuredApp(SecuredApp app) {
		if (this.app == null) {
			this.app = app;
		} else {
			try {
				sendLine("Close", "Invalid action.", "You can't change your application while being in one, we've gone ahead and closed the current one for you.");
			} catch (IOException e) {
				e.printStackTrace();
			}
			close();
		}
	}

	public void setLoadedFile(File loadedFile) {
		this.loadedFile = loadedFile;
	}

	public OutputHandler getOutput() {
		return output;
	}
}
