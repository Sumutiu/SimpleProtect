package com.sumutiu.simpleprotect.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// A piston can't push, pull or break blocks inside a protection, or push blocks into one, unless the piston
// is inside that protection too. It then acts as if the blocks couldn't move (a sticky piston just retracts).
// This checks vanilla's own list of blocks to move, so slime and honey contraptions are covered too.
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.mixins.json), never called from code
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean simpleprotect$checkProtections(PistonStructureResolver resolver, Operation<Boolean> original,
                                                   Level level, BlockPos pos, Direction direction, boolean extending) {
        if (!original.call(resolver)) return false;
        if (!SimpleProtectConfig.getProtectFromPistons() || !ProtectionsManager.hasAny()) return true;

        String dim = ProtectionsManager.dimensionId(level);
        Direction push = resolver.getPushDirection();

        for (BlockPos moved : resolver.getToPush()) {
            if (ProtectionsManager.cannotAffect(pos, moved, dim) || ProtectionsManager.cannotAffect(pos, moved.relative(push), dim)) {
                return false;
            }
        }

        for (BlockPos destroyed : resolver.getToDestroy()) {
            if (ProtectionsManager.cannotAffect(pos, destroyed, dim)) {
                return false;
            }
        }
        return true;
    }
}
