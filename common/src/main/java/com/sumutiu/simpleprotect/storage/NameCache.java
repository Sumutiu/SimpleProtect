package com.sumutiu.simpleprotect.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sumutiu.simpleprotect.SimpleProtect.STORAGE_FOLDER;
import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

/**
 * The last known name of every player who joined (UUID -> name), so offline players can be found by name.
 */
public class NameCache {

    public static final String FILE_NAME = "name_cache.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<UUID, String> cache = new ConcurrentHashMap<>();

    private static Path file() {
        return STORAGE_FOLDER.resolve(FILE_NAME);
    }

    // Never fails: if the file can't be read, offline players can only be found once they joined again
    public static synchronized void load() {
        cache.clear();

        Path file = file();
        if (Files.notExists(file)) return;

        try {
            Map<String, String> raw = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            if (raw == null) return;

            for (Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    if (entry.getValue() != null) {
                        cache.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip entries that are not valid UUIDs
                }
            }
        } catch (IOException | JsonParseException e) {
            Logger(1, String.format(NAME_CACHE_LOAD_FAILED, e.getMessage()));
        }
    }

    // A player joined: remember their current name
    public static synchronized void put(UUID player, String name) {
        if (name.equals(cache.get(player))) return;

        // Names can change hands: forget older players with this name
        cache.entrySet().removeIf(e -> !e.getKey().equals(player) && e.getValue().equalsIgnoreCase(name));
        cache.put(player, name);
        save();
    }

    // The player who last joined with this name (not case-sensitive), or null
    public static UUID findByName(String name) {
        for (Map.Entry<UUID, String> entry : cache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // The player's last known name, or null
    public static String getName(UUID player) {
        return cache.get(player);
    }

    public static Collection<String> names() {
        return new ArrayList<>(cache.values());
    }

    // Server stopped: forget everything
    public static synchronized void clear() {
        cache.clear();
    }

    private static void save() {
        Map<String, String> raw = new TreeMap<>();
        cache.forEach((player, name) -> raw.put(player.toString(), name));

        Path file = file();
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.writeString(tmp, GSON.toJson(raw), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Logger(1, String.format(NAME_CACHE_SAVE_FAILED, e.getMessage()));
        }
    }
}
