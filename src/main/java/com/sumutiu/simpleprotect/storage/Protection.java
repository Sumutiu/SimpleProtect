package com.sumutiu.simpleprotect.storage;

import com.google.gson.annotations.SerializedName;
import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Protection {
    @SerializedName("x") public int x;
    @SerializedName("y") public int y;
    @SerializedName("z") public int z;
    @SerializedName("owner") public UUID owner;
    @SerializedName("ownerName") public String ownerName;
    @SerializedName("allowed") public List<UUID> allowed = new ArrayList<>();
    // Names of the allowed players (as they were when added, or when they last joined), so they can be removed while offline
    @SerializedName("allowedNames") public Map<UUID, String> allowedNames = new HashMap<>();
    @SerializedName("dimension") public String dimension; // "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"

    // Horizontal radius (from the config): a protection covers (2 x radius + 1) blocks on X and Z, at every height
    public static int radius() {
        return SimpleProtectConfig.getRadius();
    }

    public boolean contains(BlockPos pos, String dimension) {
        if (!this.dimension.equals(dimension)) {
            return false;
        }
        int radius = radius();
        int dx = Math.abs(pos.getX() - x);
        int dz = Math.abs(pos.getZ() - z);
        return dx <= radius && dz <= radius;
    }

    public boolean isCenter(BlockPos pos, String dimension) {
        return this.dimension.equals(dimension) && pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    public boolean isOwnerOrAllowed(UUID player) {
        return owner.equals(player) || allowed.contains(player);
    }

    public void addAllowed(UUID player, String name) {
        if (!allowed.contains(player)) {
            allowed.add(player);
        }
        if (name != null) {
            allowedNames.put(player, name);
        }
    }

    // Takes the player (and their stored name) off the allowed list, if they're on it
    public void removeAllowed(UUID player) {
        allowedNames.remove(player);
        allowed.remove(player);
    }

    // The allowed player with this name (not case-sensitive), or null if there is none
    public UUID findAllowedByName(String name) {
        for (UUID player : allowed) {
            String stored = allowedNames.get(player);
            if (stored != null && stored.equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    public String idString() {
        return x + ":" + y + ":" + z + ":" + owner.toString() + ":" + dimension;
    }
}
