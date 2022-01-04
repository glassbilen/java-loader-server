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

public class RequestLibrary implements Packet {
	static final String[] ONLY_SUFFIXES = new String[] { ".so", ".dylib", ".lib", ".dll" };

	@Override
	public String getPacketName() {
		return "RequestLibrary";
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

			user.getOutput().println(TextColor.RED + "User requested a library before choosing a program.");
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

			user.getOutput().println(TextColor.RED + "User requested a library but had a app that should be invalid.");
			user.close();
			return;
		}

		String library = args[0];

		try (ByteArrayOutputStream requestedLibraryStream = new ByteArrayOutputStream()) {
			try (JarFile file = new JarFile(user.getLoadedFile())) {
				Enumeration<JarEntry> entries = file.entries();

				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();

					boolean foundInvalid = true;

					for (String suffix : ONLY_SUFFIXES) {
						if (entry.getName().toLowerCase().endsWith(suffix)) {
							foundInvalid = false;
							break;
						}
					}

					if (foundInvalid) {
						continue;
					}

					String key = entry.getName();

					if (key.endsWith("/") && key.length() > 1) {
						key = key.substring(0, key.length() - 1);
					}

					int index = key.lastIndexOf('/');

					if (index != -1) {
						if (index < key.length()) {
							key = key.substring(index + 1);
						}
					}

					if (key.equals(library)) {
						try (InputStream is = file.getInputStream(entry)) {
							while (is.available() > 0) {
								requestedLibraryStream.write(is.read());
							}
						}

						break;
					}
				}
			} catch (IOException e1) {
				e1.printStackTrace();

				try {
					user.sendLine("Close", "Unexpected error!",
							"A unexpected error has occurred while serving a library, try restarting the application or try again later.");
				} catch (IOException e) {
					e.printStackTrace();
				}

				user.close();
			}

			try {
				user.sendLine("RequestedLibrary", library,
						Base64.getEncoder().encodeToString(requestedLibraryStream.toByteArray()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e2) {
			e2.printStackTrace();

			try {
				user.sendLine("Close", "Unexpected error!",
						"A unexpected error has occurred while allocating the library for you, please contact the author or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.close();
		}
	}
}
