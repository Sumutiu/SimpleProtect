package com.sumutiu.simpleprotect;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionsManager {
    private static final Path BASE_DIR = FabricLoader.getInstance().getGameDir().resolve("mods").resolve("SimpleProtect");
    private static final Path FILE = BASE_DIR.resolve("SimpleProtect.json");
    private static final Gson GSON = new Gson();
    // Map<idString, Protection>
    private static final Map<String, Protection> protections = new ConcurrentHashMap<>();

    public static void init() {
        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
        load();
    }

    public static synchronized void load() {
        if (!Files.exists(FILE)) {
            save(); // create empty file
            return;
        }
        try (Reader r = Files.newBufferedReader(FILE)) {
            Type listType = new TypeToken<List<Protection>>(){}.getType();
            List<Protection> list = GSON.fromJson(r, listType);
            protections.clear();
            if (list != null) {
                for (Protection p : list) protections.put(p.idString(), p);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static synchronized void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(new ArrayList<>(protections.values()), w);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static synchronized void addProtection(Protection p) {
        protections.put(p.idString(), p);
        save();
    }

    public static synchronized void removeProtection(Protection p) {
        protections.remove(p.idString());
        save();
    }

    // Call this after modifying a protection's state (e.g., adding an allowed player)
    public static synchronized void updateProtection(Protection p) {
        save();
    }

    public static Collection<Protection> all() { return protections.values(); }

    public static Optional<Protection> getById(String id) {
        return Optional.ofNullable(protections.get(id));
    }

    public static Optional<Protection> findByOwnerAt(UUID owner, BlockPos pos, String dimension) {
        return protections.values().stream()
                .filter(p -> p.owner.equals(owner))
                .filter(p -> p.contains(pos, dimension))
                .min(Comparator.comparingInt(p -> Math.abs(p.x - pos.getX()) + Math.abs(p.z - pos.getZ())));
    }

    public static List<Protection> protectionsContaining(BlockPos pos, String dimension) {
        List<Protection> out = new ArrayList<>();
        for (Protection p : protections.values()) {
            if (p.contains(pos, dimension)) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean isPlayerAllowedAt(UUID player, BlockPos pos, String dimension) {
        List<Protection> list = protectionsContaining(pos, dimension);
        if (list.isEmpty()) return true; // No protection here, so action is allowed

        // If there is a protection, check if the player is the owner or is in the allowed list
        for (Protection p : list) {
            if (p.owner.equals(player) || p.allowed.contains(player)) {
                return true;
            }
        }
        return false; // Player is not allowed in any of the protections at this location
    }
}
