package dev.autostore;

import dev.autostore.core.StallWatch;
import dev.autostore.core.DesyncWatch;
import dev.autostore.core.DamageWatch;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import baritone.api.utils.BlockOptionalMetaLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** User-authorized recovery only. No persisted login, no recovery from an unrelated disconnect. */
public final class MiningRecovery {
    private final Controller owner;
    private final Minecraft mc=Minecraft.getInstance();
    private final StallWatch watch=new StallWatch();
    private final DesyncWatch desync=new DesyncWatch();
    private final DamageWatch damageWatch=new DamageWatch();
    private boolean hurt;
    public void damaged(){hurt=true;}
    private final java.util.concurrent.ConcurrentLinkedQueue<Packet<?>> feedback=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final LinkedHashMap<BlockPos,BlockState> attemptedBlocks=new LinkedHashMap<>();
    private int tried,accepted,corrected;
    private ServerData server;
    private BlockOptionalMetaLookup target;
    private String scope;
    private long started,joinedAt,lastAttemptAt=Long.MIN_VALUE/2,lastRelog=Long.MIN_VALUE/2;
    private boolean connecting,cooldownAnnounced,resumeStorage;
    public MiningRecovery(Controller owner){this.owner=owner;}
    public boolean pending(){return server!=null;}
    public void cancel(){server=null;target=null;scope=null;connecting=false;resumeStorage=false;joinedAt=0;watch.reset();desync.reset();damageWatch.reset();hurt=false;feedback.clear();attemptedBlocks.clear();tried=accepted=corrected=0;lastAttemptAt=Long.MIN_VALUE/2;}
    public String status(){
        if(pending())return "Recovery: reconnecting";
        if(owner.canRecoverStorage())return "Recovery: no usable storage; relog in "
            + Math.max(0,60_000-(owner.now()-lastRelog))/1000 + "s (then 2-second reconnect countdown)";
        String damageStatus="damage " + damageWatch.damageMillis(owner.now())/1000 + "/10s";
        if(!owner.canMonitorMine()) {
            String blocked=owner.recoveryBlocker();
            return "Recovery waiting: " + (blocked!=null?blocked:owner.nearlyFull()?"inventory nearly full; storage takes priority":"combat/eating/item use")
                + "; " + damageStatus;
        }
        if(cooldownAnnounced)return "Recovery: relog retry in " + Math.max(0,60_000-(owner.now()-lastRelog))/1000 + "s";
        return "Recovery: idle " + watch.idleMillis(owner.now())/1000 + "/15s; desync "
            + desync.suspectMillis(owner.now())/1000 + "/10s; attempt samples " + desync.attemptSamples(tried) + "; " + damageStatus;
    }
    public boolean tick() {
        try{return update();}
        catch(RuntimeException error){cancel();owner.emergency("Recovery failed; start the job manually after checking inventory");return true;}
    }
    private boolean update() {
        long now=owner.now();
        if(pending()) {
            if(now-started>120_000){cancel();owner.message("Recovery login timed out; no further reconnect attempts.");return true;}
            if(mc.player!=null && mc.level!=null && mc.getConnection()!=null) {
                if(joinedAt==0){joinedAt=now;return true;}
                if(now-joinedAt<3_000 || !mc.level.hasChunkAt(mc.player.blockPosition()))return true;
                if(!scope.equals(owner.recoveryScope()) || !mc.player.isAlive()) {
                    cancel();owner.message("Recovery stopped: world or player changed.");return true;
                }
                // Deliberate recovery starts from the server's current position and inventory.
                // There is no pending deposit/tool swap when a recovery ticket is created.
                // Do not compare speculative pre-disconnect stacks or coordinates to the rejoined state.
                if(mc.player.containerMenu!=mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty())return true;
                BlockOptionalMetaLookup saved=target;
                boolean storage=resumeStorage;
                cancel();
                // The user specifically authorized restarting #mine after this deliberate relog.
                owner.baritone().getMineProcess().mine(0,saved);
                if(storage)owner.retryStorageAfterRecovery();
                watch.afterRelog();owner.message(storage?"Reconnected; retrying storage before continuing the saved #mine target.":"Reconnected; resumed the saved #mine target.");return true;
            }
            if(!connecting && now-started>=2_000) {
                if(!(mc.gui.screen() instanceof RecoveryScreen)){cancel();return true;}
                connecting=true;
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(server.ip),server,false,null);
            } else if(connecting && mc.gui.screen() instanceof DisconnectedScreen) {
                cancel();return true; // rejected login is not retried
            } else if(connecting && mc.gui.screen() instanceof TitleScreen) {
                cancel();return true; // user cancelled the connection screen
            }
            return true;
        }
        if(owner.canRecoverStorage()) {
            beginRelog(now,"No usable storage after trying registered containers; relogging, then retrying storage.",false,true);
            return pending();
        }
        drainFeedback();
        boolean damageEligible=owner.canMonitorDamageRecovery();
        boolean damageRecovery=damageWatch.sample(now,damageEligible,hurt,accepted,damageEligible?owner.minedItemCount():0);
        hurt=false;
        if(damageRecovery) {
            beginRelog(now,"Sustained damage with little mining progress; relogging and restarting the saved target.",true);
            return pending();
        }
        boolean monitoring=owner.canMonitorMine();
        if(desync.sample(now,monitoring,tried,accepted,monitoring?owner.minedItemCount():0,corrected)) {
            beginRelog(now,"Mining actions have no server-confirmed progress; relogging to recover desync.");return pending();
        }
        boolean eligible=monitoring;
        if(!eligible){watch.sample(now,false,0,0,0,0);return false;}
        Vec3 p=mc.player.position();
        switch(watch.sample(now,true,p.x,p.y,p.z,owner.minedItemCount())) {
            case NONE -> {}
            case REPATH -> {
                owner.message("No mining progress for 15 seconds; refreshing Baritone's route and block search.");
                owner.cancel();
                ((baritone.process.MineProcess)owner.baritone().getMineProcess()).recoverSearch();
            }
            case RELOG -> beginRelog(now,"Still stuck after route recovery; relogging once, then restoring #mine.");
        }
        return pending();
    }
    public void attempted(BlockPos pos,BlockState state) {
        if(!owner.canMonitorMine() || state.isAir())return;
        // A native mining path can be stuck breaking leaves or other access blocks,
        // not just the requested log/ore. Such failed attempts must count too.
        // start/continueDestroyBlock can fire every frame for the same unbreakable block.
        // Count sustained effort, not render rate or only completed local destruction.
        long now=owner.now();
        if(now-lastAttemptAt<500)return;
        lastAttemptAt=now;tried++;attemptedBlocks.put(pos.immutable(),state);
        if(attemptedBlocks.size()>128)attemptedBlocks.remove(attemptedBlocks.keySet().iterator().next());
    }
    public void received(Packet<?> packet) {
        if((packet instanceof ClientboundBlockUpdatePacket || packet instanceof ClientboundSectionBlocksUpdatePacket
            || packet instanceof ClientboundPlayerPositionPacket) && feedback.size()<128)feedback.offer(packet);
    }
    private void drainFeedback() {
        Packet<?> packet;
        while((packet=feedback.poll())!=null) {
            if(packet instanceof ClientboundBlockUpdatePacket block)confirmed(block.getPos(),block.getBlockState());
            else if(packet instanceof ClientboundSectionBlocksUpdatePacket section)section.runUpdates(this::confirmed);
            else if(packet instanceof ClientboundPlayerPositionPacket)corrected++;
        }
    }
    private void confirmed(BlockPos pos,BlockState state) {
        BlockState old=attemptedBlocks.get(pos);
        if(old!=null && state.getBlock()!=old.getBlock()){attemptedBlocks.remove(pos);accepted++;}
    }
    private void beginRelog(long now,String message) {beginRelog(now,message,false);}
    private void beginRelog(long now,String message,boolean damageEmergency) {beginRelog(now,message,damageEmergency,false);}
    private void beginRelog(long now,String message,boolean damageEmergency,boolean storageRecovery) {
        if(mc.getCurrentServer()==null || mc.getSingleplayerServer()!=null) {
            watch.reset();desync.reset();damageWatch.reset();return;
        }
        if(!damageEmergency && now-lastRelog<60_000) {
            if(!cooldownAnnounced){owner.message("Recovery cooldown; another relog will retry automatically within 60 seconds.");cooldownAnnounced=true;}
            return;
        }
        cooldownAnnounced=false;resumeStorage=storageRecovery;
        target=owner.recoveryTarget();scope=owner.recoveryScope();
        server=mc.getCurrentServer();started=now;lastRelog=now;joinedAt=0;connecting=false;
        owner.message(message);owner.suspendForRecovery();mc.disconnect(new RecoveryScreen(),false);
    }
    private final class RecoveryScreen extends Screen {
        RecoveryScreen(){super(net.minecraft.network.chat.Component.literal("AutoStore recovery"));}
        @Override protected void init() {
            addRenderableOnly(new net.minecraft.client.gui.components.StringWidget(width/2-160,height/2-35,320,20,
                net.minecraft.network.chat.Component.literal("Reconnecting in 2 seconds to recover mining"),font));
            addRenderableWidget(new net.minecraft.client.gui.components.Button.Builder(
                net.minecraft.network.chat.Component.literal("Cancel recovery"),button -> onClose())
                .bounds(width/2-100,height/2,200,20).build());
        }
        @Override public void onClose(){cancel();mc.gui.setScreen(new TitleScreen());}
    }
}
