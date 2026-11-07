package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture before Meteor's GameLeftEvent and vanilla/Baritone teardown discard the native job. */
// A later-applied HEAD injection is prepended before earlier HEAD callbacks.
@Mixin(value=Minecraft.class,priority=500,remap=false)
public abstract class ReconnectMixin {
    @Inject(method="disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",at=@At("HEAD"))
    private void rememberMiningForMeteorReconnect(Screen next,boolean transferring,boolean keepResourcePacks,CallbackInfo ci) {
        if(Controller.INSTANCE!=null)Controller.INSTANCE.beforeDisconnect(next,transferring);
    }
}
