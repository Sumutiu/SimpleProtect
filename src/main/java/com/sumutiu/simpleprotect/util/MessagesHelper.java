package com.sumutiu.simpleprotect.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagesHelper {

    // ----------------------------
    // Core / General
    // ----------------------------
    public static final String MOD_ASCII_BANNER = """
          _____ _                 _      _____           _            _  \s
         / ____(_)               | |    |  __ \\         | |          | | \s
        | (___  _ _ __ ___  _ __ | | ___| |__) | __ ___ | |_ ___  ___| |_\s
         \\___ \\| | '_ ` _ \\| '_ \\| |/ _ \\  ___/ '__/ _ \\| __/ _ \\/ __| __|
         ____) | | | | | | | |_) | |  __/ |   | | | (_) | ||  __/ (__| |_\s
        |_____/|_|_| |_| |_| .__/|_|\\___|_|   |_|  \\___/ \\__\\___|\\___|\\__|
                           | |                                           \s
                           |_|                                           \s
        """;

    public static final String Mod_ID = "[SimpleProtect]";

    // ----------------------------
    // Configuration / Storage
    // ----------------------------
    public static final String MAIN_FOLDER_CREATED = "Mod folders have been created successfully.";
    public static final String MAIN_FOLDER_CREATION_FAILED = "Failed to create the mod folder.";
    public static final String MOD_INIT_FAILED = "Mod has failed to initialize. Error in creating the mod folder.";

    public static final String PROT_FILE_READ_FAILED = "Failed to load JSON file.";
    public static final String PROT_FILE_SAVE_FAILED = "Failed to save JSON file.";

    // ----------------------------
    // Protection - General
    // ----------------------------
    public static final String PROTECTION_REMOVED = "Your protection has been removed.";
    public static final String BREAK_OTHERS_PROT = "You cannot break another player's protection block!";
    public static final String NO_BLOCK_DMG = "You cannot break blocks here!";
    public static final String PROT_OVERLAP = "Your protection would overlap with someone else's protection.";
    public static final String NEW_PROT_CONFIRM = "Created a new protection zone!";
    public static final String NO_PROT_PLACEMENT = "You cannot place a protection block here!";
    public static final String NO_INTERACTION = "You cannot interact with blocks here!";
    public static final String NO_ITEM_USE = "You cannot use this item here!";
    public static final String PROT_ENTER = "You have entered %s's protection.";
    public static final String PROT_EXIT = "You have left %s's protection.";

    // ----------------------------
    // Protection - Commands
    // ----------------------------
    public static final String NOT_IN_PROTECTION = "You are not standing inside one of your protections.";
    public static final String PLAYER_NOT_FOUND = "Player not found: %s.";
    public static final String PLAYER_ALLOWED_ALREADY = "%s is already allowed in this protection.";
    public static final String PLAYER_ADDED = "Added %s to this protection.";
    public static final String PLAYER_ADDED_CONFIRM = "You have been added to %s's protection.";
    public static final String PLAYER_REMOVED = "Removed %s from this protection.";
    public static final String PLAYER_REMOVED_CONFIRM = "You have been removed from %s's protection.";
    public static final String PLAYER_REMOVED_FAIL = "%s was not in this protection's allowed list.";

    private static final Logger LOGGER = LoggerFactory.getLogger(Mod_ID);

    // ----------------------------
    // Player messaging
    // ----------------------------
    public static void PrivateMessage(ServerPlayerEntity player, String message) {
        if (isConnected(player)) {
            player.sendMessage(Text.literal(Mod_ID + ": ")
                    .styled(style -> style.withColor(Formatting.GREEN))
                    .append(Text.literal(message).styled(s -> s.withColor(Formatting.WHITE))), false);
        }
    }

    // ----------------------------
    // Logging
    // ----------------------------
    public static void Logger(int type, String message) {
        switch (type) {
            case 0 -> LOGGER.info(message);
            case 1 -> LOGGER.warn(message);
            case 2 -> LOGGER.error(message);
        }
    }

    // ----------------------------
    // Helper Methods
    // ----------------------------
    public static String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer("simpleprotect")
                .map(ModContainer::getMetadata)
                .map(meta -> meta.getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static boolean isConnected(ServerPlayerEntity player) {
        if (player == null) return false;
        player.getEntityWorld();
        return player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }

    public static void logAsciiBanner(String banner, String footer) {
        LOGGER.info(""); // Empty line before
        for (String line : banner.stripTrailing().split("\n")) {
            LOGGER.info(line);
        }
        LOGGER.info(""); // Empty line before
        LOGGER.info(footer);
        LOGGER.info(""); // Empty line after
    }
}
