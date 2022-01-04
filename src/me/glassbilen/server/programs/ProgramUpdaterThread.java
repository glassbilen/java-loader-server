package me.glassbilen.server.programs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.glassbilen.common.security.SecuredApp;
import me.glassbilen.common.security.SecurityLevel;
import me.glassbilen.io.text.OutputHandler;
import me.glassbilen.io.text.TextColor;
import me.glassbilen.server.Main;
import me.glassbilen.server.requests.ContentType;
import me.glassbilen.server.requests.WebRequest;
import me.glassbilen.server.requests.WebRequestResult;
import me.glassbilen.server.users.UserSocket;

public class ProgramUpdaterThread extends Thread {
	private OutputHandler output;
	private List<LoadedApp> apps;
	private List<UserSocket> users;
	private String url;
	private String token;
	private File folder;
	private File tempFolder;

	private WebRequest request;

	public ProgramUpdaterThread(List<LoadedApp> apps, List<UserSocket> users, OutputHandler output, String url,
			String token, File folder, File tempFolder) {
		this.output = output;
		this.apps = apps;
		this.users = users;
		this.url = url;
		this.token = token;
		this.folder = folder;
		this.tempFolder = tempFolder;

		request = getRequest("getActiveClients");
	}

	@Override
	public void run() {
		while (Main.RUNNING) {
			WebRequestResult result = null;
			
			try {
				result = request.connect();
			} catch (NoSuchElementException | IOException | JSONException e1) {
				e1.printStackTrace();
				
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				continue;
			}
			
			try {
				JSONObject obj = new JSONObject(result.getData());

				if (obj.getBoolean("success")) {
					JSONArray programs = obj.getJSONArray("clients");
					List<String> ids = new ArrayList<>();

					for (int i = 0; i < programs.length(); i++) {
						JSONObject program = programs.getJSONObject(i);
						String id = program.getString("id");
						String name = program.getString("name");
						String hash = program.getString("hash");
						boolean exitAfterExecution = program.getBoolean("exitAfterExecution");
						boolean forceUpdates = program.getBoolean("forceUpdates");
						SecurityLevel level = SecurityLevel.valueOf(program.getString("security-mode").toUpperCase());

						boolean found = false;

						for (LoadedApp app : apps) {
							if (app.getApp().getHash().equals(id)) {
								found = true;

								if (app.getApp().getLevel() != level) {
									app.getApp().setLevel(level);
									output.println(TextColor.YELLOW + "Program[" + app.getName() + ", "
											+ app.getApp().getHash() + "] has changed security mode to "
											+ level.toString() + ".");
								}

								if (app.getApp().shouldExitAfterFinish() != exitAfterExecution) {
									app.getApp().setExitAfterFinish(exitAfterExecution);
									output.println(TextColor.YELLOW + "Program[" + app.getName() + ", "
											+ app.getApp().getHash() + "] has changed its exit after finish option to "
											+ (exitAfterExecution ? "on" : "off") + ".");
								}

								if (app.shouldForceUpdate() != forceUpdates) {
									app.setShouldForceUpdate(forceUpdates);
									output.println(TextColor.YELLOW + "Program[" + app.getName() + ", "
											+ app.getApp().getHash() + "] has changed its force update option to "
											+ (forceUpdates ? "on" : "off") + ".");
								}

								if (!app.getHash().equals(hash)) {
									if (app.shouldForceUpdate()) {
										app.updateHash(hash);
										app.setReady(false);

										for (UserSocket user : users) {
											if (user.getSecuredApp().getHash().equals(app.getApp().getHash())) {
												try {
													user.sendLine("Close", "Outdated app!",
															"A new version has arrived, please restart the app to get the latest version.");
													user.getOutput().println(TextColor.YELLOW
															+ "User was kicked since there is a newer version of the program.");
												} catch (IOException e) {
													e.printStackTrace();
												}

												user.close();
											}
										}
									} else {
										boolean active = false;

										for (UserSocket user : users) {
											if (user.getSecuredApp().getHash().equals(app.getApp().getHash())) {
												active = true;
												break;
											}
										}

										if (!active) {
											output.println(TextColor.YELLOW + "Program[" + app.getName() + ", "
													+ app.getApp().getHash()
													+ "] was just updated, forced updates is off, but no user is active so update will start.");
											app.updateHash(hash);
											app.setReady(false);
										} else {

											String uuid;
											do {
												uuid = UUID.randomUUID().toString().replaceAll("-", "") + ".jar";

												boolean foundDup = false;

												for (File file : tempFolder.listFiles()) {
													if (file.getName().equals(uuid)) {
														foundDup = true;
														break;
													}
												}

												if (!foundDup) {
													break;
												}
											} while (true);

											File newFile = Paths.get(tempFolder.getPath(), uuid).toFile();

											try {
												Files.copy(app.getFile().toPath(), newFile.toPath(),
														StandardCopyOption.REPLACE_EXISTING);

												for (UserSocket user : users) {
													if (user.getSecuredApp().getHash().equals(app.getApp().getHash())) {
														if (user.getLoadedFile().getPath()
																.equals(app.getFile().getPath())) {
															user.setLoadedFile(newFile);

															try {
																user.sendLine("Info", "Outdated app!",
																		"A new version has been released. You can get the latest version by restarting the program.");
															} catch (IOException e) {
																e.printStackTrace();
															}
														}
													}
												}

												output.println(TextColor.YELLOW + "Program[" + app.getName() + ", "
														+ app.getApp().getHash()
														+ "] was just updated, forced updates is off, existing users has been migrated to a temporary version.");

												app.updateHash(hash);
												app.setReady(false);
											} catch (IOException e) {
												e.printStackTrace();
											}
										}
									}
								}
								break;
							}
						}

						if (!found) {
							File file = Paths.get(folder.getPath(), id + ".jar").toFile();
							LoadedApp app = new LoadedApp(new SecuredApp(id, level), name, hash, file, forceUpdates);
							app.getApp().setExitAfterFinish(exitAfterExecution);

							if (isReady(app)) {
								output.println(TextColor.GREEN + "New application[" + name + ", " + id
										+ "] networked, already downloaded and SHA-1 checksum matches.");
								app.setReady(true);
							} else {
								output.println(TextColor.YELLOW + "New application networked[" + name + ", " + id
										+ "], file doesnt exist or SHA-1 checksum doesnt match.");
							}

							apps.add(app);
						}

						ids.add(id);
					}

					List<Integer> toRemove = new ArrayList<>();

					for (int i = 0; i < apps.size(); i++) {
						LoadedApp app = apps.get(i);
						boolean found = false;

						for (String id : ids) {
							if (app.getApp().getHash().equals(id)) {
								found = true;
								break;
							}
						}

						if (!found) {
							app.setReady(false);

							output.println(TextColor.YELLOW + "Removing program[" + app.getName() + ", "
									+ app.getApp().getHash() + "] since it was deleted.");

							for (UserSocket user : users) {
								if (user.getSecuredApp().getHash().equals(app.getApp().getHash())) {
									try {
										user.sendLine("Close", "Deleted app!", "This program has been deleted.");
									} catch (IOException e) {
										e.printStackTrace();
									}

									user.getOutput().println(
											TextColor.YELLOW + "User was kicked since the program was deleted.");
									user.close();
								}
							}

							toRemove.add(i);
						}
					}

					for (File temp : tempFolder.listFiles()) {
						boolean found = false;

						for (UserSocket user : users) {
							if (user.getLoadedFile().getPath().equals(temp.getPath())) {
								found = true;
								break;
							}
						}

						if (!found) {
							output.println(TextColor.YELLOW
									+ "Deleted cached version from last update since no users is on it anymore.");
							temp.delete();
						}
					}

					Iterator<Integer> iterator = toRemove.iterator();

					while (iterator.hasNext()) {
						int index = iterator.next();

						if (index >= apps.size()) {
							break;
						}

						LoadedApp appToRemove = apps.get(index);

						if (appToRemove.getFile().exists()) {
							appToRemove.getFile().delete();
						}

						output.println("A program[" + appToRemove.getName() + ", " + appToRemove.getApp().getHash()
								+ "] was removed by the program updater thread since its no longer active.");
						apps.remove(index);
					}

					for (File client : folder.listFiles()) {
						boolean found = false;

						for (String id : ids) {
							if (client.getName().equals(id + ".jar")) {
								found = true;
								break;
							}
						}

						if (!found) {
							client.delete();
						}
					}
				} else {
					output.println(TextColor.RED + "Program updater found invalid response, reason: "
							+ obj.getString("response"));
				}
			} catch (JSONException e1) {
				e1.printStackTrace();
				output.println(TextColor.RED + "Failed to parse program json.");
			}

			for (LoadedApp app : apps) {
				if (isReady(app)) {
					app.setReady(true);
					continue;
				}

				if (app.getHash().isEmpty()) {
					continue;
				}

				output.println(TextColor.YELLOW + "Downloading latest version of program[" + app.getName() + ", "
						+ app.getApp().getHash() + "].");
				update(app);

				if (isReady(app)) {
					app.setReady(true);
					output.println(TextColor.GREEN + "Successfully downloaded program[" + app.getName() + ", "
							+ app.getApp().getHash() + "].");
				} else {
					output.println(TextColor.RED + "Failed to downlaod program[" + app.getName() + ", "
							+ app.getApp().getHash() + "], hash mismatch(calculated: " + getSHA1(app) + ", expected: "
							+ app.getHash() + ").");
				}
			}

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean update(LoadedApp app) {
		WebRequest request = getRequest("getClientFile", "target=" + app.getApp().getHash());

		if (request != null) {
			try {
				request.connect(app.getFile());
				return true;
			} catch (NoSuchElementException | IOException | JSONException e) {
				e.printStackTrace();
			}
		}

		return false;
	}

	private boolean isReady(LoadedApp app) {
		if (!app.getFile().exists() || app.getHash().isEmpty()) {
			return false;
		}

		String hash = getSHA1(app);

		if (hash == null) {
			return false;
		}

		if (hash.equals(app.getHash())) {
			return true;
		}

		return false;
	}

	private String getSHA1(LoadedApp app) {
		try (InputStream fileStream = new FileInputStream(app.getFile())) {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");

			try (DigestInputStream digestStream = new DigestInputStream(fileStream, digest)) {
				byte[] buffer = new byte[8192];
				while (digestStream.read(buffer) != -1)
					;

				StringBuilder builder = new StringBuilder();
				byte[] message = digestStream.getMessageDigest().digest();

				for (byte b : message) {
					builder.append(String.format("%02x", b));
				}

				return builder.toString();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return null;
	}

	private WebRequest getRequest(String path, String... parameters) {
		StringBuilder builder = new StringBuilder();
		List<String> newParameters = new ArrayList<>();

		newParameters.add("token=" + token);
		Collections.addAll(newParameters, parameters);

		for (String parameter : newParameters) {
			if (builder.toString().isEmpty()) {
				builder.append("?");
			} else {
				builder.append("&");
			}

			String[] parts = parameter.split("=");

			if (parts.length == 2) {
				try {
					String left = URLEncoder.encode(parts[0], "UTF-8");
					String right = URLEncoder.encode(parts[1], "UTF-8");
					builder.append(left + "=" + right);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
		}

		try {
			WebRequest request = new WebRequest(url + "/api/" + path + ".php" + builder.toString(), "GET", ContentType.NONE, false);
			return request;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}
}
