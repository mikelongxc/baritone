package dev.autostore.mixin;

import dev.autostore.Controller;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=KillAura.class,remap=false)
public abstract class KillAuraMixin {
    @Shadow private boolean acceptableWeapon(ItemStack stack) { throw new AssertionError(); }

    /** This check occurs after target selection. Read the newly selected sword, not a stale argument. */
    @Redirect(method="onTick", at=@At(value="INVOKE", target="Lmeteordevelopment/meteorclient/systems/modules/combat/KillAura;acceptableWeapon(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean yieldAndSelectSword(KillAura aura, ItemStack original) {
        Controller c=Controller.INSTANCE;
        if (c!=null && c.cooperativeJob()) {
            c.beforeAuraAttack();
            return acceptableWeapon(Minecraft.getInstance().player.getMainHandItem());
        }
        return acceptableWeapon(original);
    }

    /** Mining yields to combat. The separate isUsingItem check still respects eating/pause-on-use. */
    @Redirect(method="onTick",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean allowCombatWhileMining(MultiPlayerGameMode mode) {
        Controller c=Controller.INSTANCE;
        return (c==null || !c.cooperativeJob()) && mode.isDestroying();
    }
}
