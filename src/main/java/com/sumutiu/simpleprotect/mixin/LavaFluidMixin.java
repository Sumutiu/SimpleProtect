package com.sumutiu.simpleprotect.mixin;

import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lava has its own spreadTo (lava flowing down onto water turns it into stone) that runs before FlowingFluid's.
// "require = 0": if a future version drops this method, the game still starts (FlowingFluidMixin still applies).
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.mixins.json), never called from code
@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true, require = 0)
    private void simpleprotect$noFlowIntoProtections(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target, CallbackInfo ci) {
        if (SimpleProtectConfig.getProtectFromFluids() && ProtectionsManager.cannotFlowInto(level, pos, direction)) {
            ci.cancel();
        }
    }
}
