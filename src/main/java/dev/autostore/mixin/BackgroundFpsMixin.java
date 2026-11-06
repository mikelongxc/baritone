package dev.autostore.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.autostore.Controller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FramerateLimitTracker.class,remap=false)
public abstract class BackgroundFpsMixin {
    @Inject(method="getFramerateLimit",at=@At("RETURN"),cancellable=true)
    private void keepManagedJobTicking(CallbackInfoReturnable<Integer> cir) {
        Controller c=Controller.INSTANCE;
        if(c!=null && c.keepRunningInMenus() && cir.getReturnValueI()<20)cir.setReturnValue(20);
    }
}
