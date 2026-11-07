package dev.autostore.mixin;

import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=AutoTool.class,remap=false)
public interface AutoToolAccess {
    @Invoker("shouldStopUsing") boolean autostore$shouldStopUsing(ItemStack stack);
}
