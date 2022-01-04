package me.glassbilen.server;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import me.glassbilen.common.security.SecurityLevel;
import me.glassbilen.file.ConfigManager;
import me.glassbilen.file.exceptions.ConfigOutdatedException;
import me.glassbilen.io.text.TextColor;
import me.glassbilen.server.logging.OutputHandlerFile;
import me.glassbilen.server.packets.PacketManager;
import me.glassbilen.server.programs.LoadedApp;
import me.glassbilen.server.programs.ProgramUpdaterThread;
import me.glassbilen.server.users.CleanupThread;
import me.glassbilen.server.users.UserSocket;

public class Main {
	public static boolean RUNNING = false;

	public static ConfigManager config;
	public static List<LoadedApp> apps;

	private OutputHandlerFile output;

	private List<UserSocket> users;
	private PacketManager packetManager;
	private Thread cleanupThread;
	private Thread programUpdaterThread;

	public Main() {
		output = new OutputHandlerFile(true);

		PrintStream errStream = new PrintStream(System.err) {
			@Override
			public void println(String string) {
				output.printException(string);
				super.println(string);
			}

			@Override
			public void print(String string) {
				output.printException(string);
				super.print(string);
			}
		};

		System.setErr(errStream);

		config = new ConfigManager(new File("config.cfg"));

		config.prepareInteger("max-connections-per-ip", 5, 0, Integer.MAX_VALUE, "Max user connections from the same ip at the sane time");
		config.prepareString("server-ip", "127.0.0.1", "The remote ip the server is on (ipv4).");
		config.prepareInteger("server-port", 20692, 0, 65535, "The port to listen on.");

		config.prepareString("web-panel-url", "https://www.example.com", "The url to the web panel. Does not end with \"/\".");
		config.prepareString("web-panel-token", "", "The token configured on the web panel.");

		config.prepareString("client-install-folder", "clients", "The folder to store all clients.");
		config.prepareString("client-temp-folder", "temp", "The folder to store all temporary clients.");

		StringBuilder methods = new StringBuilder();

		for (SecurityLevel level : SecurityLevel.values()) {
			if (!methods.toString().isEmpty()) {
				methods.append(", ");
			}

			methods.append(level);
		}

		try {
			config.init();

			if (config.isFresh()) {
				output.println(TextColor.YELLOW + "Config was updated, please reconfigure the app and restart the program.");
				return;
			}
		} catch (ConfigOutdatedException e) {
			output.println(TextColor.YELLOW + "Config was made for the first time, please configure the app and restart the program.");
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}

		File folder = new File(config.getString("client-install-folder"));

		if (!folder.exists()) {
			folder.mkdirs();
		}

		File tempFolder = new File(config.getString("client-temp-folder"));

		if (!tempFolder.exists()) {
			tempFolder.mkdirs();
		}

		users = new ArrayList<>();
		apps = new ArrayList<>();

		packetManager = new PacketManager();

		ServerSocket serverSocket;

		try {
			serverSocket = new ServerSocket(config.getInteger("server-port"));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
			return;
		}

		RUNNING = true;

		cleanupThread = new CleanupThread(output, users);
		cleanupThread.start();

		programUpdaterThread = new ProgramUpdaterThread(apps, users, output, config.getString("web-panel-url"), config.getString("web-panel-token"), folder, tempFolder);
		programUpdaterThread.start();

		output.println("Server is now listening on " + config.getString("server-ip") + ":" + config.getInteger("server-port") + ".");

		while (RUNNING) {
			Socket socket = null;

			try {
				socket = serverSocket.accept();

				int count = 0;

				for (UserSocket user : users) {
					if (user == null) {
						continue;
					}

					if (user.getIp().equals(socket.getInetAddress().toString().replace("/", ""))) {
						count++;
					}
				}

				boolean closeOnStart = false;

				if (count > config.getInteger("max-connections-per-ip")) {
					output.println(TextColor.RED + "Closed potential user " + socket.getInetAddress().toString().replace("/", "") + " for too many open connections.");
					closeOnStart = true;
				}

				output.println("New incoming connection from " + socket.getInetAddress().toString().replace("/", "") + "." + (closeOnStart ? " Connection will be closed after first packet." : ""));

				UserSocket user = new UserSocket(socket, packetManager, closeOnStart);
				users.add(user);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		new Main();
	}
}
