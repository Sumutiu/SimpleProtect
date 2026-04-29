package com.sumutiu.simpleprotect;

import com.sumutiu.simpleprotect.commands.ProtectionCommands;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import com.sumutiu.simpleprotect.util.EventHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class SimpleProtect implements ModInitializer {

	public static Path STORAGE_FOLDER;
	public static Path FILE;

	public static volatile boolean SimpleProtectInitialized = false;

	@Override
	public void onInitialize() {

		// -----------------------------
		// SERVER START (WORLD EXISTS)
		// -----------------------------
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {

			long seed = server.getWorldGenSettings().options().seed();

			STORAGE_FOLDER = Path.of("mods", "SimpleProtect_Seed_" + Long.toUnsignedString(seed));
			FILE = STORAGE_FOLDER.resolve("SimpleProtect.json");

			if (initPlugin()) {
				// Register player balance placeholder
				SimpleProtectInitialized = true;
			} else {
				Logger(2, MOD_INIT_FAILED);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> {
			ServerPlayer player = handler.getPlayer();

			if (!SimpleProtectInitialized) {
				player.connection.disconnect(
						Component.literal(MOD_NOT_INITIALIZED)
				);
			}
		});

		ServerTickEvents.START_SERVER_TICK.register(EventHandlers::onServerTick);
		EventHandlers.register();
		ProtectionCommands.register();
	}

	/**
	 * Initializes the mod by creating necessary files and loading protections.
	 * @return true if initialization is successful, false otherwise.
	 */
	private static boolean initPlugin() {
		logAsciiBanner(MOD_ASCII_BANNER, Mod_ID + ": V" + getModVersion() + " - Your build matters!");

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
