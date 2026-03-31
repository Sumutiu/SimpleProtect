package com.sumutiu.simpleprotect.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;
import static com.sumutiu.simpleprotect.SimpleProtect.FILE;

public class ProtectionsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map<idString, Protection>
    private static final Map<String, Protection> protections = new ConcurrentHashMap<>();

    public static synchronized boolean load() {
        try (Reader r = Files.newBufferedReader(FILE)) {

            Type listType = new TypeToken<List<Protection>>() {}.getType();
            List<Protection> list = GSON.fromJson(r, listType);

            protections.clear();

            if (list != null) {
                for (Protection p : list) {
                    protections.put(p.idString(), p);
                }
            }

            return true;

        } catch (IOException ex) {
            Logger(2, PROT_FILE_READ_FAILED);
            return false;
        }
    }

    public static synchronized boolean save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(new ArrayList<>(protections.values()), w);
            return true;
        } catch (IOException ex) {
            Logger(2, PROT_FILE_SAVE_FAILED);
            return false;
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

    public static Collection<Protection> all() {
        return new ArrayList<>(protections.values());
    }

    public static Optional<Protection> findByOwnerAt(UUID owner, BlockPos pos, String dimension) {
        return all().stream()
                .filter(p -> p.owner.equals(owner))
                .filter(p -> p.contains(pos, dimension))
                .min(Comparator.comparingInt(p ->
                        Math.abs(p.x - pos.getX()) + Math.abs(p.z - pos.getZ())
                ));
    }

    public static List<Protection> protectionsContaining(BlockPos pos, String dimension) {
        List<Protection> out = new ArrayList<>();

        for (Protection p : all()) {
            if (p.contains(pos, dimension)) {
                out.add(p);
            }
        }

        return out;
    }

    public static boolean isPlayerAllowedAt(UUID player, BlockPos pos, String dimension) {
        List<Protection> list = protectionsContaining(pos, dimension);

        if (list.isEmpty()) return true;

        for (Protection p : list) {
            if (p.owner.equals(player) || p.allowed.contains(player)) {
                return true;
            }
        }

        return false;
    }
}