package com.sumutiu.simpleprotect.mixin;

import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A dispenser can't do anything into a protection (place lava, water or fire, place blocks, shoot, shear...)
// unless it is inside that protection too. It just clicks, like an empty dispenser. Droppers are not affected.
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.mixins.json), never called from code
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void simpleprotect$checkProtections(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!SimpleProtectConfig.getProtectFromDispensers() || !ProtectionsManager.hasAny()) return;

        BlockPos front = pos.relative(state.getValue(DispenserBlock.FACING));
        if (ProtectionsManager.cannotAffect(pos, front, ProtectionsManager.dimensionId(level))) {
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
            ci.cancel();
        }
    }
}
