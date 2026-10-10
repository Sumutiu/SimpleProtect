package com.sumutiu.simpleprotect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.sumutiu.simpleprotect.SimpleProtect.CONFIG_FILE;
import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class SimpleProtectConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ConfigData {
        public Integer SimpleProtect_Radius = 30;
        public String SimpleProtect_Block = "minecraft:emerald_block";
        public Integer SimpleProtect_MaxProtectionsPerPlayer = 10;
        public Integer SimpleProtect_MinExtensionDistance = 15;
        public Boolean SimpleProtect_ProtectEntities = true;
        public Boolean SimpleProtect_ProtectFromExplosions = true;
        public Boolean SimpleProtect_ProtectFromFireSpread = true;
        public Boolean SimpleProtect_ProtectFromPistons = true;
        public Boolean SimpleProtect_ProtectFromFluids = true;
        public Boolean SimpleProtect_ProtectFromDispensers = true;
    }

    private static final ConfigData config_Default = new ConfigData();
    private static ConfigData config = new ConfigData();
    private static Block protectionBlock = Blocks.EMERALD_BLOCK;

    // Writes a new config file with the default values
    public static boolean createDefault() {
        config = new ConfigData();
        protectionBlock = Blocks.EMERALD_BLOCK;
        return save();
    }

    public static boolean save() {
        // Serialize to a String first: Gson wraps write errors in an unchecked JsonIOException
        try {
            Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8);
            Logger(0, String.format(CONFIG_SAVED, CONFIG_FILE));
            return true;
        } catch (IOException e) {
            Logger(2, String.format(CONFIG_SAVE_FAILED, e.getMessage()));
            return false;
        }
    }

    // Never fails: if the file can't be read, the defaults are used for this run
    public static void load() {
        boolean updated = false;
        try {
            String text = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            ConfigData loaded = GSON.fromJson(text, ConfigData.class);
            if (loaded != null) {
                config = loaded;

                // Check for fields set to null
                if (config.SimpleProtect_Radius == null) {
                    config.SimpleProtect_Radius = config_Default.SimpleProtect_Radius;
                    updated = true;
                }
                if (config.SimpleProtect_Block == null) {
                    config.SimpleProtect_Block = config_Default.SimpleProtect_Block;
                    updated = true;
                }
                if (config.SimpleProtect_MaxProtectionsPerPlayer == null) {
                    config.SimpleProtect_MaxProtectionsPerPlayer = config_Default.SimpleProtect_MaxProtectionsPerPlayer;
                    updated = true;
                }
                if (config.SimpleProtect_MinExtensionDistance == null) {
                    config.SimpleProtect_MinExtensionDistance = config_Default.SimpleProtect_MinExtensionDistance;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectEntities == null) {
                    config.SimpleProtect_ProtectEntities = config_Default.SimpleProtect_ProtectEntities;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectFromExplosions == null) {
                    config.SimpleProtect_ProtectFromExplosions = config_Default.SimpleProtect_ProtectFromExplosions;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectFromFireSpread == null) {
                    config.SimpleProtect_ProtectFromFireSpread = config_Default.SimpleProtect_ProtectFromFireSpread;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectFromPistons == null) {
                    config.SimpleProtect_ProtectFromPistons = config_Default.SimpleProtect_ProtectFromPistons;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectFromFluids == null) {
                    config.SimpleProtect_ProtectFromFluids = config_Default.SimpleProtect_ProtectFromFluids;
                    updated = true;
                }
                if (config.SimpleProtect_ProtectFromDispensers == null) {
                    config.SimpleProtect_ProtectFromDispensers = config_Default.SimpleProtect_ProtectFromDispensers;
                    updated = true;
                }

                // Check for fields missing from the file (Gson keeps the default value for those,
                // so they are never null): write them to the file
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                for (String key : GSON.toJsonTree(config).getAsJsonObject().keySet()) {
                    if (!json.has(key)) {
                        updated = true;
                    }
                }

                Logger(0, CONFIG_LOADED);
            } else {
                Logger(1, CONFIG_LOAD_FAILED_MALFORMED);
                config = new ConfigData();
                updated = true;
            }
        } catch (IOException | JsonParseException e) {
            // Use the defaults for this run, but never overwrite a file the admin may be editing
            Logger(2, String.format(CONFIG_LOAD_FAILED, e.getMessage()));
            config = new ConfigData();
            protectionBlock = Blocks.EMERALD_BLOCK;
            return;
        }

        // Save updated file if defaults were added
        if (updated) save();

        validate();
    }

    // Out-of-range values are replaced in memory only (with a warning), the file is left as it is
    private static void validate() {
        config.SimpleProtect_Radius = atLeast("SimpleProtect_Radius", config.SimpleProtect_Radius, 1);
        config.SimpleProtect_MaxProtectionsPerPlayer = atLeast("SimpleProtect_MaxProtectionsPerPlayer", config.SimpleProtect_MaxProtectionsPerPlayer, 0);
        config.SimpleProtect_MinExtensionDistance = atLeast("SimpleProtect_MinExtensionDistance", config.SimpleProtect_MinExtensionDistance, 0);

        protectionBlock = findBlock(config.SimpleProtect_Block);
        if (protectionBlock == null) {
            Logger(1, String.format(CONFIG_INVALID_BLOCK, config.SimpleProtect_Block, config_Default.SimpleProtect_Block));
            protectionBlock = Blocks.EMERALD_BLOCK;
        }
    }

    private static int atLeast(String key, int value, int minimum) {
        if (value >= minimum) return value;
        Logger(1, String.format(CONFIG_INVALID_VALUE, key, value, minimum));
        return minimum;
    }

    // The block with this id, or null if there is none or it can't be placed by a player (air, fire...)
    private static Block findBlock(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;

        Block block = BuiltInRegistries.BLOCK.get(identifier).map(Holder::value).orElse(null);
        return block == null || block.asItem() == Items.AIR ? null : block;
    }

    // How far a protection reaches from its block, on X and Z
    public static int getRadius() {
        return config.SimpleProtect_Radius;
    }

    public static Block getProtectionBlock() {
        return protectionBlock;
    }

    // Counts every protection block, extensions too. 0 = no limit
    public static int getMaxProtectionsPerPlayer() {
        return config.SimpleProtect_MaxProtectionsPerPlayer;
    }

    // A protection block placed inside your own protection extends it only this far from your other protection blocks
    public static int getMinExtensionDistance() {
        return config.SimpleProtect_MinExtensionDistance;
    }

    public static boolean getProtectEntities() {
        return config.SimpleProtect_ProtectEntities;
    }

    public static boolean getProtectFromExplosions() {
        return config.SimpleProtect_ProtectFromExplosions;
    }

    public static boolean getProtectFromFireSpread() {
        return config.SimpleProtect_ProtectFromFireSpread;
    }

    public static boolean getProtectFromPistons() {
        return config.SimpleProtect_ProtectFromPistons;
    }

    public static boolean getProtectFromFluids() {
        return config.SimpleProtect_ProtectFromFluids;
    }

    public static boolean getProtectFromDispensers() {
        return config.SimpleProtect_ProtectFromDispensers;
    }
}
