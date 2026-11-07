package dev.autostore.mixin;

import dev.autostore.Controller;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ClientPacketListener.class,remap=false)
public abstract class ContainerSnapshotMixin {
    @Inject(method="handleBlockUpdate",at=@At("TAIL"))
    private void trailBlock(net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket p,CallbackInfo ci){if(dev.autostore.trail.TrailController.INSTANCE!=null)dev.autostore.trail.TrailController.INSTANCE.onBlock(p.getPos(),p.getBlockState());}
    @Inject(method="handleChunkBlocksUpdate",at=@At("TAIL"))
    private void trailBlocks(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket p,CallbackInfo ci){if(dev.autostore.trail.TrailController.INSTANCE!=null)p.runUpdates(dev.autostore.trail.TrailController.INSTANCE::onBlock);}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void afterServerSlot(ClientboundContainerSetSlotPacket packet,CallbackInfo ci) {
        if(Controller.INSTANCE!=null)Controller.INSTANCE.onSlotSnapshot(packet);
    }
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void afterServerContent(ClientboundContainerSetContentPacket packet,CallbackInfo ci) {
        if(Controller.INSTANCE!=null) Controller.INSTANCE.onSnapshot(packet,(ClientPacketListener)(Object)this);
        if(dev.autostore.trail.TrailController.INSTANCE!=null)dev.autostore.trail.TrailController.INSTANCE.onSnapshot(packet);
    }
}
