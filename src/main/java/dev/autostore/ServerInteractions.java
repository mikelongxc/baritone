package dev.autostore;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/** Send the actual interaction-facing rotation before useItemOn, independent of rendered frames. */
public final class ServerInteractions {
    private ServerInteractions(){}
    public static void face(Minecraft mc,Vec3 point) {
        Vec3 d=point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround(),mc.player.horizontalCollision));
    }
}
