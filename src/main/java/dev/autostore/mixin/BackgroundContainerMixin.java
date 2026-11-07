package dev.autostore.mixin;

import dev.autostore.Controller;
import dev.autostore.trail.TrailController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla installs player.containerMenu before presenting its Screen. Background
 * automation needs that server menu, not native cursor operations or Screen.init. */
@Mixin(value=Gui.class,remap=false)
public abstract class BackgroundContainerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private Screen screen;
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void keepOwnedBackgroundContainerHeadless(Screen next,CallbackInfo ci) {
        if(minecraft.isWindowActive())return;
        boolean owned=Controller.INSTANCE!=null && Controller.INSTANCE.ownsStorageMenu()
            || TrailController.INSTANCE!=null && TrailController.INSTANCE.ownsSupplyMenu();
        if(!owned)return;
        if(next==null && screen==null){ci.cancel();return;} // close the server menu without a pointless native GUI transition
        if(minecraft.player!=null && next instanceof MenuAccess<?> access
            && access.getMenu()==minecraft.player.containerMenu
            && (access.getMenu() instanceof ChestMenu || access.getMenu() instanceof ShulkerBoxMenu))ci.cancel();
    }
}
