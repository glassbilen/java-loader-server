package me.glassbilen.server.packets.types;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import me.glassbilen.common.security.SecurityLevel;
import me.glassbilen.io.text.TextColor;
import me.glassbilen.network.CNetworkManager;
import me.glassbilen.network.Packet;
import me.glassbilen.server.Main;
import me.glassbilen.server.programs.LoadedApp;
import me.glassbilen.server.users.UserSocket;

public class RequestProgramPacket implements Packet {
	static final String[] STRIP_SUFFIXES = new String[] { "class" };

	@Override
	public String getPacketName() {
		return "RequestProgram";
	}

	@Override
	public int getArgsWanted() {
		return 1;
	}

	@Override
	public void onReceive(CNetworkManager manager, String[] args) {
		UserSocket user = (UserSocket) manager;

		LoadedApp found = null;

		for (LoadedApp app : Main.apps) {
			if (app.getApp().getHash().equals(args[0])) {
				found = app;
				break;
			}
		}

		if (found == null) {
			try {
				user.sendLine("Close", "Invalid program!",
						"The requested program has either been moved, deleted or hasnt been refreshed yet, please contact the author or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(TextColor.RED + "User tried accessing non existing program[" + args[0] + "].");
			user.close();
			return;
		}

		user.getOutput()
				.println("User requested a program[" + found.getName() + ", " + found.getApp().getHash() + "].");

		if (!found.isReady()) {
			try {
				user.sendLine("Close", "Not ready yet!",
						"The requested program is currently being updated, please try again in a few minutes.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(
					TextColor.YELLOW + "User tried accessing a program that isn't ready yet[" + args[0] + "].");
			user.close();
			return;
		}

		String manifest = getMainClass(found.getFile());

		if (manifest == null) {
			try {
				user.sendLine("Close", "Invalid program!",
						"The requested program misses a main method, please contact the author or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(TextColor.RED + "User requested a program missing a manifest file["
					+ found.getName() + ", " + found.getApp().getHash() + "].");
			user.close();
			return;
		}

		String className = "";
		String prefix = "Main-Class: ";

		for (String line : manifest.split("(\\r\\n|\\r|\\n)")) {
			if (line.startsWith(prefix)) {
				className = line.substring(prefix.length());
			}
		}

		if (className.isEmpty()) {
			try {
				user.sendLine("Close", "Invalid program!",
						"The requested program has a invalid manifest file, please contact the author or try again later.");
			} catch (IOException e) {
				e.printStackTrace();
			}

			user.getOutput().println(
					TextColor.RED + "User requested a program with a invalid manifest file, missing main class["
							+ found.getName() + ", " + found.getApp().getHash() + "].");
			user.close();
			return;
		}

		String mainClass = cleanClass(className);

		user.setLoadedFile(found.getFile());
		user.setSecuredApp(found.getApp());

		try {
			user.sendLine("ProgramData", user.getSecuredApp().getLevel().toString(),
					user.getSecuredApp().shouldExitAfterFinish(), mainClass);

			if (user.getSecuredApp().getLevel() == SecurityLevel.FULL_JAR) {
				sendAllClasses(user, user.getLoadedFile());
			}

			user.getOutput().println("User has successfully been sent the requested program.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getMainClass(File jar) {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			try (JarFile file = new JarFile(jar)) {
				Enumeration<JarEntry> entries = file.entries();

				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();

					if (entry.isDirectory()) {
						continue;
					}

					String name = RequestProgramPacket.cleanClass(entry.getName());

					if (name.equalsIgnoreCase("META-INF.MANIFEST.MF")) {
						try (InputStream is = file.getInputStream(entry)) {
							while (is.available() > 0) {
								stream.write(is.read());
							}
						}

						return new String(stream.toByteArray());
					}
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return null;
	}

	private void sendAllClasses(UserSocket user, File program) {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			try (JarFile file = new JarFile(program)) {
				Enumeration<JarEntry> entries = file.entries();

				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();

					if (entry.isDirectory()) {
						continue;
					}

					boolean found = false;

					for (String suffix : STRIP_SUFFIXES) {
						if (entry.getName().toLowerCase().endsWith(suffix)) {
							found = true;
							break;
						}
					}

					String name = RequestProgramPacket.cleanClass(entry.getName());
					stream.reset();

					try (InputStream is = file.getInputStream(entry)) {
						while (is.available() > 0) {
							stream.write(is.read());
						}
					}

					String packet = "RequestedClass";

					if (!found) {
						packet = "RequestedResource";
						name = entry.getName();
					}

					user.sendLine(packet, name, Base64.getEncoder().encodeToString(stream.toByteArray()));
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	protected static String cleanClass(String clazz) {
		String newClazz = clazz;

		for (String suffix : STRIP_SUFFIXES) {
			newClazz = stripExtension(clazz, suffix);
		}

		return newClazz.replaceAll("/", ".");
	}

	protected static String stripExtension(String string, String suffix) {
		String newSuffix = "." + suffix;

		if (string.toLowerCase().endsWith(newSuffix.toLowerCase())) {
			return string.substring(0, string.length() - newSuffix.length());
		}

		return string;
	}
}
