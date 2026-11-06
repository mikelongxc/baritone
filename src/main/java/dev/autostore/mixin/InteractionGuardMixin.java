package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MultiPlayerGameMode.class,priority=2100,remap=false)
public abstract class InteractionGuardMixin {
    @Inject(method="handleContainerInput",at=@At("HEAD"),cancellable=true)
    private void reserveOwnedMenu(int id,int slot,int button,ContainerInput input,Player player,CallbackInfo ci) {
        if(Controller.INSTANCE!=null && !Controller.INSTANCE.permitContainerClick()) ci.cancel();
    }
    @Inject(method="attack",at=@At("HEAD"))
    private void combatPriority(Player player,Entity target,CallbackInfo ci) {
        if(Controller.INSTANCE!=null) Controller.INSTANCE.onCombatAttack();
    }
}
