package dev.autostore.mixin;

import dev.autostore.Controller;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=AutoTool.class,remap=false)
public abstract class AutoToolMixin {
    @Shadow private boolean shouldSwitch;
    @Shadow private boolean wasPressed;
    @Inject(method="shouldStopUsing",at=@At("RETURN"),cancellable=true)
    private void respectMiningReserve(ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        Controller c=Controller.INSTANCE;
        if(c!=null && !c.canUseMiningTool(stack)) cir.setReturnValue(true);
    }
    @Inject(method="onTick",at=@At("HEAD"),cancellable=true)
    private void yieldHeldSlot(TickEvent.Post event,CallbackInfo ci) {
        Controller c=Controller.INSTANCE;
        if(c!=null && c.deferAutoTool()) {
            // Discard an obsolete queued mining swap; do not replay it into food or a combat weapon.
            shouldSwitch=false; wasPressed=false; ci.cancel();
        }
    }
}
