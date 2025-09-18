package com.sumutiu.simpleprotect;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sumutiu.simpleprotect.MessagesHelper.*;

public class SimpleProtect implements ModInitializer {

	public static final Path STORAGE_FOLDER = Path.of("mods", "SimpleProtect");
	public static final Path FILE = STORAGE_FOLDER.resolve("SimpleProtect.json");

	@Override
	public void onInitialize() {
		if (initPlugin()) {

			// Register events + commands
			EventHandlers.register();
			ProtectionCommands.register();

			// Tick loop for enter/leave detection
			ServerTickEvents.START_SERVER_TICK.register(EventHandlers::onServerTick);
		} else {
			Logger(2, MOD_INIT_FAILED);
		}
	}

	/**
	 * Initializes the mod by creating necessary files and loading protections.
	 * @return true if initialization is successful, false otherwise.
	 */
	private static boolean initPlugin() {
		logAsciiBanner(MOD_ASCII_BANNER, "[SimpleProtect]: V" + getModVersion() + " - Your build matters!");

		try {
			if (Files.notExists(STORAGE_FOLDER)) {
				Files.createDirectories(STORAGE_FOLDER);
				Logger(0, MAIN_FOLDER_CREATED);
			}
		} catch (IOException e) {
			Logger(2, MAIN_FOLDER_CREATION_FAILED);
			return false;
		}

		if (Files.notExists(FILE)) {
			if (!ProtectionsManager.save()) {
				return false;
			}
		}

		return ProtectionsManager.load();
	}
}
