package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=MultiPlayerGameMode.class,priority=2000,remap=false)
public abstract class BreakGuardMixin {
    @Inject(method={"startDestroyBlock","continueDestroyBlock"},at=@At("HEAD"),cancellable=true)
    private void beforeBreak(BlockPos pos,Direction face,CallbackInfoReturnable<Boolean> cir) {
        Controller c=Controller.INSTANCE;
        if(c!=null) {
            if(!c.permitBreak(pos))cir.setReturnValue(false);
            else c.observeBreakAttempt(pos);
        }
    }
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void beforeDestroy(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        Controller c=Controller.INSTANCE;
        if(c!=null && (!c.permitBreak(pos) || !c.reserveToolBreak(pos)))cir.setReturnValue(false);
    }
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void afterDestroy(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(Boolean.TRUE.equals(cir.getReturnValue()) && Controller.INSTANCE!=null) Controller.INSTANCE.broken();
    }
}
