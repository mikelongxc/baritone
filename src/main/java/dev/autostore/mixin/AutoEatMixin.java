package dev.autostore.mixin;

import dev.autostore.Controller;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AutoEat.class,remap=false)
public abstract class AutoEatMixin {
    @Inject(method="onTick",at=@At("HEAD"),cancellable=true)
    private void coordinateEating(TickEvent.Pre event,CallbackInfo ci) {
        Controller c=Controller.INSTANCE;
        if(c==null || !c.cooperativeJob()) return;
        AutoEat eat=(AutoEat)(Object)this;
        if(c.beforeEating(eat.eating,eat.eating || eat.shouldEat())) ci.cancel();
    }
}
