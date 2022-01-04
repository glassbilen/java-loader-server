package me.glassbilen.server.programs;

import java.io.File;

import me.glassbilen.common.security.SecuredApp;

public class LoadedApp {
	private SecuredApp app;
	private String name;
	private String hash;
	private File file;
	private boolean ready;
	private boolean shouldForceUpdate;

	public LoadedApp(SecuredApp app, String name, String hash, File file, boolean shouldForceUpdate) {
		this.app = app;
		this.name = name;
		this.hash = hash;
		this.file = file;
		this.shouldForceUpdate = shouldForceUpdate;
	}

	public SecuredApp getApp() {
		return app;
	}

	public String getName() {
		return name;
	}

	public String getHash() {
		return hash;
	}

	public void updateHash(String hash) {
		this.hash = hash;
	}

	public boolean isReady() {
		return ready;
	}

	public File getFile() {
		return file;
	}

	public boolean shouldForceUpdate() {
		return shouldForceUpdate;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setShouldForceUpdate(boolean shouldForceUpdate) {
		this.shouldForceUpdate = shouldForceUpdate;
	}
}
