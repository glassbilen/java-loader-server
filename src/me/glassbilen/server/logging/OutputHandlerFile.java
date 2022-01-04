package me.glassbilen.server.logging;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import me.glassbilen.file.FileHandler;
import me.glassbilen.io.text.OutputHandler;

public class OutputHandlerFile extends OutputHandler {
	private FileHandler errorFile;
	private FileHandler logFile;

	public OutputHandlerFile(boolean showTime) {
		super(showTime);
	}

	@Override
	public void printRaw(String message) {
		super.printRaw(message);
		try {
			initDate(logFile, "logs").writeToFile(message.replaceAll("\u001B\\[[;\\d]*m", "") + System.lineSeparator(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void printRawLine(String message) {
		super.printRawLine(message);
		try {
			initDate(logFile, "logs").writeToFile(message.replaceAll("\u001B\\[[;\\d]*m", "") + System.lineSeparator(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void printException(String exception) {
		try {
			initDate(errorFile, "errors").writeToFile(
					getTimePrefix() + exception.replaceAll("\u001B\\[[;\\d]*m", "") + System.lineSeparator(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private FileHandler initDate(FileHandler file, String folder) throws IOException {
		String oldDate = file == null ? "" : file.getFile().getName();
		String curDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now());

		if (!oldDate.equals(curDate)) {
			file = new FileHandler(new File(folder + "/" + curDate + ".log"));
			file.init();
		}

		return file;
	}
}
