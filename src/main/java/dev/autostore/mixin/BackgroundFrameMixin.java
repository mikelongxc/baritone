package dev.autostore.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.autostore.Controller;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the normal game-thread loop independent of background surface presentation.
 * run() still polls window events; runTick() still processes packets/tasks and calls tick().
 * Never fake focus, capture the mouse, or run game mutations from a worker thread. */
@Mixin(value=Minecraft.class,priority=2100,remap=false)
public abstract class BackgroundFrameMixin {
    @Shadow @Final private DeltaTracker.Timer deltaTracker;
    @Shadow protected abstract void pick(float partialTick);

    @Inject(method="renderFrame",at=@At("HEAD"),cancellable=true)
    private void keepBotRunningWithoutDrawing(boolean advanceGameTime,CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        Controller c=Controller.INSTANCE;
        if(c==null || !c.keepRunningInMenus() || mc.isWindowActive())return;
        // No world/UI extraction, map drawing, acquireNextTexture, or swapBuffers while
        // unfocused. Those operations can stall the SAME thread that drives deposits.
        deltaTracker.advanceRealTime(Util.getMillis());
        mc.gui.update();
        if(mc.level!=null && mc.player!=null)pick(1.0F);
        // Resource/render tasks must still be drained; no surface presentation is needed.
        RenderSystem.executePendingTasks();
        FramerateLimiter.limitDisplayFPS(20);
        ci.cancel();
    }
}
