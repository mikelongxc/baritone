package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=Gui.class,remap=false)
public abstract class PauseMenuMixin {
    @Redirect(method="isPausing",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;isPauseScreen()Z"))
    private boolean keepWorldRunningBehindPassiveScreen(Screen screen) {
        Controller c=Controller.INSTANCE;
        if(Controller.passiveScreen(screen) && c!=null && c.keepRunningInMenus())return false;
        return screen.isPauseScreen();
    }
}
