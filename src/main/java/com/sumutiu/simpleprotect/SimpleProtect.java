package com.sumutiu.simpleprotect;

import com.sumutiu.simpleprotect.commands.ProtectionCommands;
import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.NameCache;
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
    public static Path CONFIG_FOLDER;
    public static Path CONFIG_FILE;

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
            CONFIG_FOLDER = Path.of("config", "SimpleProtect_Seed_" + Long.toUnsignedString(seed));
            CONFIG_FILE = CONFIG_FOLDER.resolve("SimpleProtect.json");

            if (initPlugin()) {
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
                return;
            }

            NameCache.put(player.getUUID(), player.getName().getString());
            ProtectionsManager.updateNames(player.getUUID(), player.getName().getString());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) ->
                EventHandlers.onDisconnect(handler.getPlayer().getUUID())
        );

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!SimpleProtectInitialized) return;
            EventHandlers.onServerTick(server);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> {
            SimpleProtectInitialized = false;
            EventHandlers.clear();
            ProtectionsManager.clear();
            NameCache.clear();
        });

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

        try {
            if (Files.notExists(CONFIG_FOLDER)) {
                Files.createDirectories(CONFIG_FOLDER);
                Logger(0, CONFIG_FOLDER_CREATED);
            }
        } catch (IOException e) {
            Logger(2, CONFIG_FOLDER_CREATION_FAILED);
            return false;
        }

        if (Files.notExists(CONFIG_FILE)) {
            if (!SimpleProtectConfig.createDefault()) {
                return false;
            }
            Logger(0, DEFAULT_CONFIG_CREATED);
        }
        SimpleProtectConfig.load();

        NameCache.load();

        if (Files.notExists(FILE)) {
            // A new world starts with no protections (not the ones of a world opened earlier in this game)
            ProtectionsManager.clear();
            if (!ProtectionsManager.save()) {
                return false;
            }
        }

        return ProtectionsManager.load();
    }
}
