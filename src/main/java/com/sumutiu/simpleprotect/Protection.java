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

    public boolean contains(BlockPos pos, String dimension) {
        if (!this.dimension.equals(dimension)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - x);
        int dz = Math.abs(pos.getZ() - z);
        return dx <= H_RADIUS && dz <= H_RADIUS;
    }

    public String idString() {
        return x + ":" + y + ":" + z + ":" + owner.toString() + ":" + dimension;
    }
}
