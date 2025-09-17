package com.sumutiu.simpleprotect;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Protection {
    @SerializedName("x") public int x;
    @SerializedName("y") public int y;
    @SerializedName("z") public int z;
    @SerializedName("owner") public UUID owner;
    @SerializedName("allowed") public List<UUID> allowed = new ArrayList<>();
    @SerializedName("dimension") public String dimension; // "overworld", "the_nether", "the_end"

    public static final int H_RADIUS = 30; // horizontal radius

    public boolean contains(BlockPos pos) {
        if (!dimensionMatches(pos)) return false; // caller should pass world-dim info
        int dx = Math.abs(pos.getX() - x);
        int dz = Math.abs(pos.getZ() - z);
        return dx <= H_RADIUS && dz <= H_RADIUS;
    }

    private boolean dimensionMatches(BlockPos pos) {
        // actual world-dimension mapping handled by caller; kept simple here
        return true;
    }

    public String idString() {
        return x + ":" + y + ":" + z + ":" + owner.toString() + ":" + dimension;
    }
}
