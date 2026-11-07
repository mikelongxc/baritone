package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Minecraft.class,remap=false)
public abstract class BackgroundMiningMixin {
    @Inject(method="pauseIfInactive",at=@At("HEAD"),cancellable=true)
    private void keepNativeJobRunningWhenUnfocused(CallbackInfo ci) {
        Controller c=Controller.INSTANCE;
        if(c!=null && c.keepRunningInMenus()) ci.cancel();
    }
}
