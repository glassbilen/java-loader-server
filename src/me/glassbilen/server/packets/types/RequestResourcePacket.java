package me.glassbilen.server.packets.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import me.glassbilen.io.text.TextColor;
import me.glassbilen.network.CNetworkManager;
import me.glassbilen.network.Packet;
import me.glassbilen.server.Main;
import me.glassbilen.server.programs.LoadedApp;
import me.glassbilen.server.users.UserSocket;

public class RequestResourcePacket implements Packet {

	@Override
	public String getPacketName() {
		return "RequestResource";
	}

	@Override
	public int getArgsWanted() {
		return 1;
	}

	@Override
	public void onReceive(CNetworkManager manager, String[] args) {
		UserSocket user = (UserSocket) manager;

		if (user.getSecuredApp() == null || !user.getSecuredApp().isReady()) {
			try {
				user.sendLine("Close", "Invalid permission!",
						"The program has done a invalid action, please contact the developer or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(TextColor.RED + "User requested a resource before choosing a program.");
			user.close();
			return;
		}

		LoadedApp found = null;

		for (LoadedApp app : Main.apps) {
			if (app.getApp().getHash().equals(user.getSecuredApp().getHash())) {
				found = app;
				break;
			}
		}

		if (found == null) {
			try {
				user.sendLine("Close", "Invalid permission!",
						"A unexpected bug has occurred, please contact the developer or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(TextColor.RED + "User requested a resource but had a app that should be invalid.");
			user.close();
			return;
		}

		String resource = args[0];

		try (ByteArrayOutputStream requestedResourceStream = new ByteArrayOutputStream()) {
			try (JarFile file = new JarFile(user.getLoadedFile())) {
				Enumeration<JarEntry> entries = file.entries();

				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();

					boolean foundInvalid = false;

					for (String suffix : RequestProgramPacket.STRIP_SUFFIXES) {
						if (entry.getName().toLowerCase().endsWith(suffix)) {
							foundInvalid = true;
							break;
						}
					}

					if (foundInvalid) {
						continue;
					}

					if (entry.getName().equals(resource)) {
						try (InputStream is = file.getInputStream(entry)) {
							while (is.available() > 0) {
								requestedResourceStream.write(is.read());
							}
						}

						break;
					}
				}
			} catch (IOException e1) {
				e1.printStackTrace();

				try {
					user.sendLine("Close", "Unexpected error!",
							"A unexpected error has occurred while serving a resource, try restarting the application or try again later.");
				} catch (IOException e) {
					e.printStackTrace();
				}

				user.close();
			}

			try {
				user.sendLine("RequestedResource", resource,
						Base64.getEncoder().encodeToString(requestedResourceStream.toByteArray()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e2) {
			e2.printStackTrace();

			try {
				user.sendLine("Close", "Unexpected error!",
						"A unexpected error has occurred while allocating the resource for you, please contact the author or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.close();
		}
	}
}
