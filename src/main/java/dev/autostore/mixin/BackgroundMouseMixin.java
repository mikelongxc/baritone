package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu transitions should not query global OS cursor/window coordinates for an unfocused bot. */
@Mixin(value=MouseHandler.class,remap=false)
public abstract class BackgroundMouseMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean ignoreFirstMove;
    @Inject(method="resyncMousePosition",at=@At("HEAD"),cancellable=true)
    private void avoidBackgroundNativeCursorQuery(CallbackInfo ci) {
        if(Controller.INSTANCE!=null && Controller.INSTANCE.keepRunningInMenus() && !minecraft.isWindowActive()) {
            ignoreFirstMove=true;ci.cancel();
        }
    }
}
