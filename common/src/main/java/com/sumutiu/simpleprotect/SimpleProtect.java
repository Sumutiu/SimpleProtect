package com.sumutiu.simpleprotect;

import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.NameCache;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import com.sumutiu.simpleprotect.util.EventHandlers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

/**
 * The mod itself, shared by Fabric and NeoForge.
 * Each loader's entrypoint (SimpleProtectFabric, SimpleProtectNeoForge) calls init() once
 * and forwards its events to the on...() methods below and to EventHandlers.
 */
public class SimpleProtect {

    public static final String MOD_ID = "simpleprotect";

    public static Path STORAGE_FOLDER;
    public static Path FILE;
    public static Path CONFIG_FOLDER;
    public static Path CONFIG_FILE;

    public static volatile boolean SimpleProtectInitialized = false;

    private static String modVersion = "unknown";

    // Called by the loader entrypoint when the mod loads
    public static void init(String version) {
        modVersion = version;
    }

    public static String getModVersion() {
        return modVersion;
    }

    // -----------------------------
    // SERVER START (WORLD EXISTS)
    // -----------------------------
    public static void onServerStarted(MinecraftServer server) {

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
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (!SimpleProtectInitialized) {
            player.connection.disconnect(
                    Component.literal(MOD_NOT_INITIALIZED)
            );
            return;
        }

        NameCache.put(player.getUUID(), player.getName().getString());
        ProtectionsManager.updateNames(player.getUUID(), player.getName().getString());
    }

    public static void onPlayerLeave(ServerPlayer player) {
        EventHandlers.onDisconnect(player.getUUID());
    }

    // Start of every server tick
    public static void onServerTick(MinecraftServer server) {
        if (!SimpleProtectInitialized) return;
        EventHandlers.onServerTick(server);
    }

    public static void onServerStopped() {
        SimpleProtectInitialized = false;
        EventHandlers.clear();
        ProtectionsManager.clear();
        NameCache.clear();
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
