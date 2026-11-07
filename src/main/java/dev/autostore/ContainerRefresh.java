package dev.autostore;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;

/** A stale-state, no-slot PICKUP requests vanilla's full menu resync without closing it.
 * Slot -1 is a no-op; it is deliberately NOT the outside/drop slot (-999).
 * Vanilla can omit slot echoes for correctly predicted clicks, so silence is not an acknowledgement. */
public final class ContainerRefresh {
    private ContainerRefresh(){}
    public static ServerboundContainerClickPacket packet(int menu) {
        return new ServerboundContainerClickPacket(menu,-1,(short)-1,(byte)0,ContainerInput.PICKUP,Int2ObjectMaps.emptyMap(),HashedStack.EMPTY);
    }
}
