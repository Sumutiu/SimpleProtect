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

	// Function that initializes the plugin storage
	private static boolean initPlugin() {
		logAsciiBanner(MOD_ASCII_BANNER, "[SimpleProtect]: V" + getModVersion() + " - Your build matters!");

		try {
			// create folder if it doesn't exist
			if (!Files.exists(STORAGE_FOLDER)) {
				Files.createDirectories(STORAGE_FOLDER);
				Logger(0, MAIN_FOLDER_CREATED);
			}

			// create empty JSON file if it doesn't exist
			if (!Files.exists(FILE)) {
				if (!ProtectionsManager.save()) {
					Logger(2, MAIN_FILE_CREATION_FAILED);
					return false;
				}
			}

			// load protections from file
			if (!ProtectionsManager.load()) {
				Logger(2, MAIN_FILE_CREATION_FAILED);
				return false;
			}

			return true;
		} catch (IOException e) {
			Logger(2, MAIN_FOLDER_CREATION_FAILED);
			return false;
		}
	}
}
