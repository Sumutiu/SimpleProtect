package com.sumutiu.simpleprotect.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    public static final String MOD_INIT_FAILED = "Mod has failed to initialize (see the error above). Players can't join until it is fixed.";

    public static final String PROT_FILE_READ_FAILED = "Failed to load the protections file %s: %s. Fix or remove the file and restart; it was not changed.";
    public static final String PROT_FILE_SAVE_FAILED = "Failed to save the protections file: %s";
    public static final String PROT_ENTRIES_SKIPPED = "Skipped %d broken protection(s) in the protections file (no owner or dimension). The original file was copied to %s.";
    public static final String PROT_BACKUP_FAILED = "Found %d broken protection(s) in the protections file, but could not make a copy of it: %s";
    public static final String NAME_CACHE_LOAD_FAILED = "Failed to load the player name cache (offline players can be found again once they join): %s";
    public static final String NAME_CACHE_SAVE_FAILED = "Failed to save the player name cache: %s";

    public static final String CONFIG_FOLDER_CREATED = "Config folder has been created.";
    public static final String CONFIG_FOLDER_CREATION_FAILED = "Failed to create the config folder.";
    public static final String DEFAULT_CONFIG_CREATED = "Default config created. Edit it in the config folder to change the settings.";
    public static final String CONFIG_SAVED = "Saved configuration file: %s";
    public static final String CONFIG_SAVE_FAILED = "Failed to save SimpleProtect config: %s";
    public static final String CONFIG_LOADED = "Successfully loaded configuration.";
    public static final String CONFIG_LOAD_FAILED = "Failed to load SimpleProtect config (using the defaults for now): %s";
    public static final String CONFIG_LOAD_FAILED_MALFORMED = "Failed to load configuration due to malformed JSON. Loading default settings.";
    public static final String CONFIG_INVALID_VALUE = "Config value %s = %d is not valid, using %d instead.";
    public static final String CONFIG_INVALID_BLOCK = "Config value SimpleProtect_Block = %s is not a block, using %s instead.";

    // ----------------------------
    // Protection - General
    // ----------------------------
    public static final String PLAYER_ONLY_COMMAND = "This command can only be used by players.";
    public static final String PROTECTION_REMOVED = "Your protection has been removed.";
    public static final String BREAK_OTHERS_PROT = "You cannot break another player's protection block!";
    public static final String MOD_NOT_INITIALIZED = "SimpleProtect mod is not initialized. Try again later.";
    public static final String NO_BLOCK_DMG = "You cannot break blocks here!";
    public static final String PROT_OVERLAP = "Placed as a normal block: a protection here would overlap with someone else's protection.";
    public static final String NEW_PROT_CONFIRM = "Created a new protection zone!";
    public static final String PROT_EXTENDED = "Extended your protection!";
    public static final String EXTENSION_TOO_CLOSE = "Placed as a normal block: to extend your protection, a protection block must be at least %d blocks from your other protection blocks.";
    public static final String PROT_LIMIT_REACHED = "Placed as a normal block: you already have the maximum of %d protection blocks.";
    public static final String PROTECTION_RESTORED = "Your protection block is back in place.";
    public static final String NO_PROT_PLACEMENT = "You cannot place a protection block here!";
    public static final String NO_INTERACTION = "You cannot interact with blocks here!";
    public static final String NO_BLOCK_PLACE = "You cannot place blocks here!";
    public static final String NO_ENTITY_INTERACTION = "You cannot do that here!";
    public static final String NO_ITEM_USE = "You cannot use this item here!";
    public static final String PROT_ENTER = "You have entered %s's protection.";
    public static final String PROT_EXIT = "You have left %s's protection.";
    public static final String MOD_INIT_NOT_READY = "Mod has not initialized.";

    // ----------------------------
    // Protection - Commands
    // ----------------------------
    public static final String NOT_IN_PROTECTION = "You are not standing inside one of your protections.";
    public static final String PLAYER_NOT_FOUND = "Player not found: %s.";
    public static final String PLAYER_ALLOWED_ALREADY = "%s is already allowed in this protection.";
    public static final String PLAYER_IS_OWNER = "You already own this protection.";
    public static final String PLAYER_ADDED = "Added %s to this protection.";
    public static final String PLAYER_ADDED_CONFIRM = "You have been added to %s's protection.";
    public static final String PLAYER_REMOVED = "Removed %s from this protection.";
    public static final String PLAYER_REMOVED_CONFIRM = "You have been removed from %s's protection.";
    public static final String PLAYER_REMOVED_FAIL = "%s was not in this protection's allowed list.";
    public static final String NOT_INSIDE_ANY_PROTECTION = "You are not standing inside a protection.";
    public static final String INFO_OWNER = "This is %s's protection (%d protection block(s)).";
    public static final String INFO_ALLOWED = "Allowed players: %s";
    public static final String INFO_NOBODY = "nobody";
    public static final String LIST_HEADER = "%s's protection blocks (%d):";
    public static final String LIST_ENTRY = "- %d, %d, %d in %s";
    public static final String LIST_EMPTY = "%s has no protections.";

    // ----------------------------
    // Protection - Admin
    // ----------------------------
    public static final String BYPASS_ON = "Admin bypass is on: you can build in every protection. Run the command again to turn it off.";
    public static final String BYPASS_OFF = "Admin bypass is off.";
    public static final String ADMIN_REMOVED_PROTECTION_BLOCK = "Removed one of %s's protection blocks.";
    public static final String PROTECTION_DELETED = "Deleted %s's protection (%d protection block(s)).";
    public static final String LOG_PROTECTION_DELETED = "%s deleted %s's protection (%d protection block(s)).";

    private static final Logger LOGGER = LoggerFactory.getLogger(Mod_ID);

    // ----------------------------
    // Player messaging
    // ----------------------------
    public static void PrivateMessage(ServerPlayer player, String message) {
        if (isConnected(player)) {
            player.sendSystemMessage(
                    Component.literal(Mod_ID + ": ")
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN))
                            .append(Component.literal(message)
                                    .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
            );
        }
    }

    // ----------------------------
    // Logging
    // ----------------------------
    public static void Logger(int type, String message) {
        switch (type) {
            case 0 -> LOGGER.info(Mod_ID + ": {}", message);
            case 1 -> LOGGER.warn(Mod_ID + ": {}", message);
            case 2 -> LOGGER.error(Mod_ID + ": {}", message);
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

    public static boolean isConnected(ServerPlayer player) {
        return player != null && player.connection.getPlayer() == player;
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
