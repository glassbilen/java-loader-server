package me.glassbilen.server.users;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.glassbilen.io.text.OutputHandler;
import me.glassbilen.server.Main;

public class CleanupThread extends Thread {
	private OutputHandler output;
	private List<UserSocket> users;

	public CleanupThread(OutputHandler output, List<UserSocket> users) {
		this.output = output;
		this.users = users;
	}

	@Override
	public void run() {
		while (Main.RUNNING) {
			if (users != null) {
				cleanupUsers();
			}

			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void cleanupUsers() {
		List<Integer> removalIndexes = new ArrayList<>();

		for (int i = 0; i < users.size(); i++) {
			if (!users.get(i).isRunning() || users.get(i).getSocket().isClosed()) {
				removalIndexes.add(i);
			}
		}

		Iterator<Integer> iterator = removalIndexes.iterator();

		while (iterator.hasNext()) {
			int index = iterator.next();

			if (index >= users.size()) {
				break;
			}
			
			UserSocket socketToClose = users.get(index);

			output.println("A connection[" + socketToClose.getIdentifier() + "] was removed by the cleanup thread for inactive for too long.");
			users.remove(index);
		}
	}
}
