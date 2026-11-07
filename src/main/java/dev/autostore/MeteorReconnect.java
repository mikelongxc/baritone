package dev.autostore;

import dev.autostore.core.ReconnectResume;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;

/** Meteor owns retry timing and login. We only restore the interrupted native job after rejoin. */
public final class MeteorReconnect {
    private final Controller owner;
    private final Minecraft mc=Minecraft.getInstance();
    private final ReconnectResume handoff=new ReconnectResume();
    private long lastSnapshotRequest=Long.MIN_VALUE/2;
    public MeteorReconnect(Controller owner){this.owner=owner;}
    public boolean enabled(){AutoReconnect module=Modules.get().get(AutoReconnect.class);return module!=null && module.isActive();}
    public boolean pending(){return handoff.pending();}
    public void cancel(){handoff.cancel();lastSnapshotRequest=Long.MIN_VALUE/2;}
    public void capture(ReconnectResume.Job job){handoff.capture(job,mc.getConnection());lastSnapshotRequest=Long.MIN_VALUE/2;}
    public void snapshot(ClientboundContainerSetContentPacket packet){
        if(packet.containerId()==0)handoff.snapshot(mc.getConnection());
    }
    public boolean tick() {
        if(!pending())return false;
        // Returning to the server list/title or switching off Meteor's module cancels the handoff.
        if(!enabled() || mc.gui.screen() instanceof TitleScreen || mc.gui.screen() instanceof JoinMultiplayerScreen){cancel();return false;}
        if(mc.player==null || mc.level==null || mc.gameMode==null || mc.getCurrentServer()==null)return true;
        var connection=mc.getConnection();
        if(!handoff.newConnection(connection))return true;
        // Never overwrite another job started while the reconnect was pending.
        var bot=owner.baritone();
        if(java.util.List.of(bot.getMineProcess(),bot.getBuilderProcess(),bot.getCustomGoalProcess(),bot.getExploreProcess(),
                bot.getFollowProcess(),bot.getGetToBlockProcess(),bot.getFarmProcess(),bot.getElytraProcess())
                .stream().anyMatch(baritone.api.process.IBaritoneProcess::isActive)
            || dev.autostore.trail.TrailController.INSTANCE!=null && dev.autostore.trail.TrailController.INSTANCE.active()) {cancel();return false;}
        if(!mc.player.isAlive() || mc.player.isCreative() || mc.player.isSpectator()){cancel();return false;}
        String scope=owner.recoveryScope();
        if(!handoff.matchesScope(scope)){cancel();return false;}
        boolean ready=mc.level.hasChunkAt(mc.player.blockPosition())
            && mc.player.containerMenu==mc.player.inventoryMenu && mc.player.inventoryMenu.getCarried().isEmpty();
        long now=owner.now();
        if(ready && !handoff.confirmed(connection) && now-lastSnapshotRequest>=1_000) {
            lastSnapshotRequest=now;connection.send(ContainerRefresh.packet(0));
        }
        ReconnectResume.Job job=handoff.poll(now,connection,scope,ready);
        if(job==null)return pending();
        owner.baritone().getMineProcess().mineByName(job.quantity(),job.targets().toArray(String[]::new));
        if(job.storageFirst())owner.retryStorageAfterRecovery();
        owner.message("Meteor reconnected; resumed the saved #mine target"+(job.storageFirst()?" with storage first.":"."));
        return false;
    }
}
