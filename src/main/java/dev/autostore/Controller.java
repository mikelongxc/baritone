package dev.autostore;

import baritone.api.*;
import baritone.api.pathing.goals.*;
import baritone.api.utils.BlockOptionalMetaLookup;
import dev.autostore.core.*;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.orbit.EventPriority;
import baritone.api.process.*;
import dev.autostore.mixin.AutoToolAccess;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import java.util.*;

import static dev.autostore.core.Loop.SafetyException;

/** All game mutations run on the client thread. Network events only latch an interrupt flag. */
public final class Controller implements Loop.Port {
    public static Controller INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();
    private final ProfileStore store = new ProfileStore(FabricLoader.getInstance().getConfigDir().resolve("autostore"));
    public final Loop loop = new Loop(this);
    public final MiningTools miningTools=new MiningTools(this);
    private final MiningRecovery recovery=new MiningRecovery(this);
    private final dev.autostore.platform.IdleSleepGuard sleepGuard=new dev.autostore.platform.IdleSleepGuard();
    private int restoreScreenKind;
    private net.minecraft.client.gui.screens.Screen restoreMapScreen;
    private boolean restorePauseMenu;
    private final DepotSelector depotSelector = new DepotSelector();
    public volatile Profile profile;
    public volatile boolean armed;
    private boolean ownsScreen, listenerAttached, ownClick;
    private AbstractContainerMenu startupContainer;
    private final Set<Integer> combatDamageSlots = new HashSet<>();
    private long combatUntil;
    private BlockOptionalMetaLookup savedFilter;
    private NativeJob nativeJob=NativeJob.NONE;
    private final ClearAreaInventory clearInventory=new ClearAreaInventory();
    private Snapshot pendingClick;
    private int pendingSlot=-1;
    private long pendingSince, snapshotRevision, fullSnapshotRevision, pendingRevision;
    private long snapshotWaitSince=-1,lastSnapshotRequest=Long.MIN_VALUE/2;
    private boolean batchRejected;
    public boolean toolJobActive(){return nativeJob.active(baritone().getMineProcess().isActive(),baritone().getBuilderProcess().isActive());}
    public boolean clearing(){return nativeJob==NativeJob.CLEAR_AREA;}
    public String toolStatus(){return miningTools.status();}
    public String recoveryStatus(){return recovery.status();}
    private boolean manuallyPaused;
    private IBaritoneProcess cooperativePause, storageProcess;
    private volatile boolean pathFailed;
    private volatile boolean interrupted;
    private volatile int playerEntityId = -1;
    private final java.util.concurrent.atomic.AtomicReference<ClientboundDamageEventPacket> lastDamage = new java.util.concurrent.atomic.AtomicReference<>();
    private volatile String hookStopReason;
    private Vec3 lastPosition;
    private Object connection;
    private long broken;
    private int combatReturnSlot=-1, combatSwordSlot=-1;
    private int expectedDepot = -1, lastClosedId = -1;
    private Snapshot snapshot, before;
    private final Set<Integer> clickedPlayerSlots = new LinkedHashSet<>();
    private Goal travelGoal;
    private BlockPos lastTravelProgressPosition;
    private int travellingDepot=-1;
    private boolean alternateApproach;
    private final Set<Policy.Pos> failedApproaches=new HashSet<>();
    private long travelProgressCounter, lastTravelBroken;
    private Loop.State lastAnnounced;
    private final Map<Settings.Setting<?>, Object> oldSettings = new LinkedHashMap<>();
    private final Map<Settings.Setting<?>, Object> enforced = new LinkedHashMap<>();
    private record Snapshot(int id, List<ItemStack> items) {}
    public long now() { return System.nanoTime()/1_000_000; }
    public IBaritone baritone() { return BaritoneAPI.getProvider().getPrimaryBaritone(); }
    public static Policy.Pos pos(BlockPos p) { return new Policy.Pos(p.getX(),p.getY(),p.getZ()); }
    public static BlockPos pos(Policy.Pos p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public static String itemId(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
    private static String blockId(Block b) { return BuiltInRegistries.BLOCK.getKey(b).toString(); }
    private void require(boolean ok, String reason) { if (!ok) throw new SafetyException(reason); }
    private String scope() {
        require(mc.player != null && mc.level != null && mc.getConnection() != null, "Join a world first");
        String world;
        if (mc.getSingleplayerServer() != null) world = "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else { require(mc.getCurrentServer() != null, "Cannot identify server scope"); world = "server:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT); }
        return ProfileStore.scope(world, mc.player.getUUID().toString(), mc.level.dimension().toString());
    }
    public void load() {
        require(!loop.active(), "Stop before loading a profile");
        armed = false;
        profile = store.load(scope());
        loop.state = Loop.State.PAUSED; loop.reason = profile.reason;
    }
    public void save() { require(profile != null, "Load a profile first"); store.save(profile); }
    public void editable() {
        require(!loop.active(), "Use #stop before changing registered storage");
        armed = false;
        if (profile == null || !Policy.sameScope(profile.scope,scope())) load();
    }
    public void arm() {
        if (profile == null || !Policy.sameScope(profile.scope,scope())) load();
        require(!loop.active(), "Already running");
        profile.validate(scope()); validateIds();
        if (!listenerAttached) {
            cooperativePause = new IBaritoneProcess() {
                public boolean isActive() { return armed && loop.active() && busy(); }
                public boolean isTemporary() { return true; }
                public double priority() { return 200; }
                public PathingCommand onTick(boolean failed, boolean safe) {
                    baritone().getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                public void onLostControl() {}
                public String displayName0() { return "AutoStore: eating/combat priority"; }
            };
            baritone().getPathingControlManager().registerProcess(cooperativePause);
            storageProcess = new IBaritoneProcess() {
                public boolean isActive() { return armed && Cooperation.storageOwnsPath(loop.state); }
                public boolean isTemporary() { return true; } // retain MineProcess targets, blacklist and branch point
                public double priority() { return 100; }
                public PathingCommand onTick(boolean failed,boolean safe) {
                    if(failed && (loop.state==Loop.State.RETURNING || loop.state==Loop.State.REOPENING)) pathFailed=true;
                    if((loop.state==Loop.State.RETURNING || loop.state==Loop.State.REOPENING) && travelGoal!=null && !pathFailed)
                        return new PathingCommand(travelGoal,PathingCommandType.SET_GOAL_AND_PATH);
                    baritone().getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null,PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                public void onLostControl() {
                    if(isActive()) hookStopReason="Baritone cancelled the storage trip; waiting for a new #mine";
                }
                public String displayName0() {return "AutoStore: temporary storage trip";}
            };
            baritone().getPathingControlManager().registerProcess(storageProcess);
            listenerAttached = true;
        }
        armed = true;
        loop.reason = "Ready; run #mine minecraft:sand";
    }
    public void disarm() {
        if (armed || loop.active()) loop.pause("Disarmed by user");
        require(stopped(), "Pathing is still stopping; try disarm again shortly");
        armed = false; releaseSettings();
    }
    public void resume() {
        require(manualResume(),"No paused native job; use #mine to start one");
    }
    /** Explicit user mine request: finish supervision of the old job BEFORE native Baritone replaces it. */
    public void beforeNativeMine(int quantity,BlockOptionalMetaLookup filter) {
        if(filter==null) return;
        beforeNativeBuild();
    }
    /** Notification only, called AFTER Baritone starts its own mine process. */
    public void observeNativeMine(int quantity,BlockOptionalMetaLookup filter) {
        if(filter==null || !baritone().getMineProcess().isActive()) return;
        nativeJob=NativeJob.MINE;miningTools.begin();
        if(quantity!=0)return;
        try { observeFilter(filter); }
        catch(RuntimeException e) {
            // Missing storage/configuration must not cancel or replace the user's native miner.
            armed=false;releaseSettings();sleepGuard.close();loop.state=Loop.State.PAUSED;
            loop.reason=e instanceof SafetyException?e.getMessage():"Storage unavailable";
            message("Baritone keeps mining; auto-storage inactive: "+loop.reason);
        }
    }
    private void observeFilter(BlockOptionalMetaLookup filter) {
        if(profile==null || !Policy.sameScope(profile.scope,scope())) load();
        savedFilter = filter;
        depotSelector.clear();
        profile.savedTargets = filter.blocks().stream().map(baritone.api.utils.BlockOptionalMeta::asCommandArgument).toList();
        profile.targets = new LinkedHashSet<>(filter.blocks().stream().map(b -> blockId(b.getBlock())).toList());
        // Use Baritone's own drop lookup, plus the block item for Silk Touch.
        profile.outputs = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack sample = new ItemStack(item);
            if (item != Items.AIR && !special(sample) && !profile.protectedItems.contains(itemId(sample)) && filter.has(sample)) profile.outputs.add(itemId(sample));
        }
        for (var block : filter.blocks()) {
            ItemStack sample = new ItemStack(block.getBlock().asItem());
            if (!sample.isEmpty() && !special(sample) && !profile.protectedItems.contains(itemId(sample))) profile.outputs.add(itemId(sample));
        }
        require(!profile.outputs.isEmpty(), "No safe item drops found for this target");
        startObservedJob();
    }
    /** Runs before any builder replacement, including non-clearing builds. */
    public void beforeNativeBuild() {
        recovery.cancel();startupContainer=null;
        manuallyPaused=false;miningTools.reset();
        if(loop.active())loop.pause("Replaced by a new native job");
        armed=false;nativeJob=NativeJob.NONE;clearInventory.reset();savedFilter=null;
        pendingClick=null;before=null;clickedPlayerSlots.clear();
    }
    /** Only #sel ca opts a builder job into storage; arbitrary builds never do. */
    public void observeSelectionClear() {
        if(!baritone().getBuilderProcess().isActive())return;
        nativeJob=NativeJob.CLEAR_AREA;miningTools.begin();
        try {
            if(profile==null || !Policy.sameScope(profile.scope,scope()))load();
            profile.outputs=new LinkedHashSet<>();
            for(Item item:BuiltInRegistries.ITEM) {
                ItemStack sample=new ItemStack(item);
                if(item!=Items.AIR && !special(sample) && !profile.protectedItems.contains(itemId(sample)))profile.outputs.add(itemId(sample));
            }
            clearInventory.begin(inventoryStacks(),profile.protectedSlots);
            startObservedJob();
        } catch(RuntimeException failure) {
            armed=false;releaseSettings();sleepGuard.close();loop.state=Loop.State.PAUSED;
            loop.reason=failure instanceof SafetyException?failure.getMessage():"Storage unavailable";
            message("Selection clearing continues; auto-storage inactive: "+loop.reason);
        }
    }
    private void startObservedJob() {
        if (!armed) arm();
        lastClosedId=-1; snapshot=null; before=null; clickedPlayerSlots.clear(); combatDamageSlots.clear();
        interrupted = false; hookStopReason = null; lastPosition = mc.player.position(); connection = mc.getConnection();
        manuallyPaused = false;
        playerEntityId = mc.player.getId(); lastDamage.set(null);
        loop.travelTimeout = profile.travelTimeoutMs; loop.openTimeout = profile.openTimeoutMs;
        enforce(BaritoneAPI.getSettings().censorCoordinates,true);
        loop.observeMining(now());
        // An explicit new job may arrive while the previous chest is still open.
        // Keep supervision armed, then close only that exact chest after the cursor clears.
        var menu=mc.player.containerMenu;
        startupContainer=menu!=mc.player.inventoryMenu && (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu)?menu:null;
        if(loop.active() && sleepGuard.supported() && !sleepGuard.start()) message("Could not prevent idle sleep; check macOS sleep settings.");
    }
    private void finishStartupContainer() {
        if(startupContainer==null)return;
        if(!cooperativeJob() || !miningActive() || mc.player==null || mc.player.containerMenu!=startupContainer) {
            startupContainer=null;return;
        }
        if(manuallyPaused || before!=null || ownsScreen || !startupContainer.getCarried().isEmpty()
            || !mc.player.inventoryMenu.getCarried().isEmpty())return;
        startupContainer=null;
        mc.player.closeContainer();
    }
    public boolean manuallyPaused() { return manuallyPaused; }
    public void manualPause() {
        recovery.cancel();
        if (loop.active()) { manuallyPaused = true; loop.yieldForActivity(now()); }
    }
    public boolean manualResume() {
        if (!manuallyPaused || !loop.active()) return false;
        manuallyPaused=false;return true;
    }
    public void manualStop() {
        recovery.cancel();
        manuallyPaused = false;miningTools.reset();
        if (armed || loop.active()) emergency("Stopped by #stop");
        armed = false;nativeJob=NativeJob.NONE;
    }
    @EventHandler(priority = EventPriority.HIGHEST) private void mobDamage(TickEvent.Pre event) {
        ClientboundDamageEventPacket damage=lastDamage.getAndSet(null);
        if (damage==null || !cooperativeJob() || interrupted || mc.player==null || mc.level==null) return;
        recovery.damaged();
        KillAura aura=Modules.get().get(KillAura.class);
        if (aura!=null && aura.isActive() && mc.level.getEntity(damage.sourceCauseId()) instanceof net.minecraft.world.entity.Mob) {
            onCombatAttack();
            baritone().getInputOverrideHandler().clearAllKeys();
            beforeAuraAttack();
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) private void tick(TickEvent.Pre event) {
        finishStartupContainer();
        if(recovery.tick()) {
            if(!recovery.pending() && !cooperativeJob())sleepGuard.close();
            return;
        }
        miningTools.tick();
        if (!armed) {if(!recovery.pending())sleepGuard.close();return;}
        if (hookStopReason != null) { String why=hookStopReason;hookStopReason=null;emergency(why); }
        if (loop.active() && mc.gameMode != null && busy()) mc.gameMode.stopDestroyBlock();
        if(combatReturnSlot>=0 && mc.player!=null && !busy()) {
            if(mc.player.getInventory().getSelectedSlot()==combatSwordSlot) mc.player.getInventory().setSelectedSlot(combatReturnSlot);
            combatReturnSlot=-1; combatSwordSlot=-1;
        }
        loop.tick(now());
        if(!loop.active()) {armed=false;sleepGuard.close();releaseSettings();}
        if (loop.state != lastAnnounced) {
            lastAnnounced = loop.state;
            if (switch(loop.state) { case MINING, RETURNING, REOPENING, WAITING_STORAGE, FAULTED, PAUSED -> true; default -> false; })
                message(loop.state+": "+statusReason());
        }
    }
    @EventHandler private void left(GameLeftEvent event) {
        if (armed) { interrupted = true; emergency("Disconnected; manual reconciliation required"); }
    }
    @EventHandler(priority = EventPriority.LOWEST) private void sendingMiningPacket(PacketEvent.Send event) {
        if(!mc.isSameThread() || !miningTools.active() || !(event.packet instanceof ServerboundPlayerActionPacket packet))return;
        if(packet.getAction()!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            && packet.getAction()!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)return;
        if(miningTools.waiting()){event.cancel();return;}
        ItemStack held=mc.player.getMainHandItem();
        if(MiningTools.belowFloor(held)){event.cancel();miningTools.stopForWornTool(held);}
    }
    @EventHandler private void received(PacketEvent.Receive event) {
        // Network callbacks only latch data; hotbar and world access happen on the client tick.
        if(armed && loop.active())recovery.received(event.packet);
        if (armed && event.packet instanceof ClientboundDamageEventPacket damage && damage.entityId()==playerEntityId) lastDamage.set(damage);
        if (armed && loop.active() && (event.packet instanceof ClientboundRespawnPacket)) interrupted = true;
    }
    public void emergency(String reason) {
        recovery.cancel();
        try { loop.pause(reason); } catch (RuntimeException ignored) { loop.state = Loop.State.FAULTED; loop.reason = "Stopped; checkpoint could not be written"; }
        finally {sleepGuard.close();releaseSettings();}
    }
    public void message(String text) { if (mc.player != null) ChatUtils.info("[AutoStore] " + text); }
    @Override public void validate() {
        require(profile != null, "Load a profile"); profile.validate(scope()); validateIds();
        require(armed && !interrupted && connection == mc.getConnection(), "Connection changed; start manually after inspection");
        require(mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.isCreative(), "Use a living survival player");
        // Screens/containers are transient activity waits, never a reason to disarm a new job.
        require(nativeJob!=NativeJob.NONE && (clearing() || savedFilter!=null), "No observed native job");
    }
    private void validateIds() {
        for (String id : profile.targets) require(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)) && !id.equals("minecraft:air"), "Unknown target block");
        for (String id : profile.outputs) {
            require(BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)), "Unknown output item");
            ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
            require(!special(sample) && !profile.protectedItems.contains(id), "Output conflicts with protected equipment or supplies");
        }
    }
    public String statusReason() {
        if(manuallyPaused) return "Paused by #pause";
        if(miningTools.waiting())return miningTools.reason();
        if (!loop.yielding()) return loop.reason;
        if (mc.player == null) return "Waiting for a world";
        if (externalScreen()) return loop.state == Loop.State.OPENING ? "At storage; put down the cursor stack or close the other container" : "Waiting for the other container or blocking screen";
        if (eatingRequested() || mc.player.isUsingItem()) return "Waiting for eating or item use";
        KillAura aura=Modules.get().get(KillAura.class);
        if (now()<combatUntil || aura!=null && aura.isActive() && aura.attacking) return "Waiting for combat";
        return "Waiting for another activity";
    }
    private boolean eatingRequested() {
        AutoEat eat = Modules.get().get(AutoEat.class);
        return eat != null && eat.isActive() && (eat.eating || eat.shouldEat());
    }
    @Override public boolean busy() {
        if (!armed || mc.player == null) return false;
        KillAura aura = Modules.get().get(KillAura.class);
        AutoGap gap = Modules.get().get(AutoGap.class);
        return miningTools.waiting() || manuallyPaused || clearing() && baritone().getBuilderProcess().isPaused() || Cooperation.yieldJob(armed, mc.player.isUsingItem(), eatingRequested() || gap != null && gap.isEating(),
            aura != null && aura.isActive() && aura.attacking || now() < combatUntil)
            || externalScreen();
    }
    /** Called before AutoEat can move food or claim the held slot. Never toggles any module. */
    public boolean beforeEating(boolean alreadyEating, boolean wantsFood) {
        if(miningTools.swapping() && !alreadyEating)return true;
        if (!armed || !loop.active()) return false;
        if (Cooperation.deferNewEating(true, before != null || miningTools.swapping(), alreadyEating)) return true;
        if (alreadyEating || wantsFood) {
            yieldSafely();
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        }
        return false;
    }
    public boolean deferAutoTool() {
        if(dev.autostore.trail.TrailController.INSTANCE!=null && dev.autostore.trail.TrailController.INSTANCE.controlsTool())return true;
        return miningTools.waiting() || armed && loop.active() && (loop.state != Loop.State.MINING && loop.state != Loop.State.RETURNING || busy());
    }
    public boolean canUseMiningTool(ItemStack stack) { return (!miningTools.active() || MiningTools.healthy(stack)) && (!storageRouting() || durable(stack)); }
    public boolean allowBuildingStack(ItemStack stack) {
        return !storageRouting() || !special(stack) && !profile.protectedItems.contains(itemId(stack));
    }
    public boolean allowNativeMiningTool(ItemStack stack) { return (!miningTools.active() || safeSpare(stack)) && (!storageRouting() || usableTool(stack)); }
    public boolean storageRouting() { return armed && Cooperation.storageOwnsPath(loop.state); }
    public boolean keepRunningInMenus() {return dev.autostore.trail.TrailController.INSTANCE!=null && dev.autostore.trail.TrailController.INSTANCE.background() || recovery.pending() || cooperativeJob() && !manuallyPaused;}
    public boolean canMonitorMine() {
        return canMonitorDamageRecovery() && !busy() && !nearlyFull();
    }
    /** Combat/eating must not hide sustained damage. Transactions and explicit stops still exclude relog. */
    public boolean canMonitorDamageRecovery() {return recoveryBlocker()==null;}
    /** The same predicate supplies both eligibility and coordinate-free diagnostics. */
    public String recoveryBlocker() {return recoveryBlocker(Loop.State.MINING);}
    private String recoveryBlocker(Loop.State requiredState) {
        if(mc.player==null || mc.level==null)return "no world/player";
        if(!mc.player.isAlive())return "player is not alive";
        if(interrupted || connection!=mc.getConnection())return "connection/world changed";
        if(nativeJob!=NativeJob.MINE)return "no supervised unlimited #mine target";
        if(!cooperativeJob())return "job supervision is stopped: " + loop.reason;
        if(loop.state!=requiredState)return "storage state " + loop.state;
        if(!miningActive())return "native Baritone mining ended";
        if(manuallyPaused)return "explicit #pause";
        if(miningTools.waiting())return "tool guard: " + miningTools.reason();
        if(before!=null)return "deposit awaiting confirmation";
        if(ownsScreen)return "owned storage menu";
        if(externalScreen())return "another container or blocking screen";
        if(mc.player.containerMenu!=mc.player.inventoryMenu)return "another container";
        if(!mc.player.inventoryMenu.getCarried().isEmpty())return "cursor holds an item";
        return null;
    }
    public boolean canRecoverStorage() {
        return recoveryBlocker(Loop.State.WAITING_STORAGE)==null && mc.getCurrentServer()!=null
            && mc.getSingleplayerServer()==null && hasDepositOutput();
    }
    public void retryStorageAfterRecovery() {
        if(cooperativeJob() && hasDepositOutput())loop.retryStorage(now());
    }
    private boolean hasDepositOutput() {
        for(int i=0;i<36;i++)if(depositable(mc.player.getInventory().getItem(i),i))return true;
        return false;
    }
    public String recoveryScope(){return scope();}
    public BlockOptionalMetaLookup recoveryTarget(){return savedFilter;}
    public int minedItemCount(){int n=0;for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i);if(profile.outputs.contains(itemId(s)))n+=s.getCount();}return n;}
    public void suspendForRecovery(){
        // Do not call emergency/manualStop: those deliberately cancel a pending recovery ticket.
        loop.pause("Deliberate stuck-recovery relog");armed=false;miningTools.reset();nativeJob=NativeJob.NONE;
    }
    public boolean cooperativeJob() { return armed && loop.active(); }
    public void onCombatAttack() {
        if (!cooperativeJob() || mc.player == null) return;
        combatUntil = now()+350;
        if (before != null) combatDamageSlots.add(mc.player.getInventory().getSelectedSlot());
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        // A sent deposit is verified once; otherwise close our screen and yield immediately.
        yieldSafely();
    }
    private void yieldSafely() {
        try { loop.yieldForActivity(now()); }
        catch (RuntimeException failure) { emergency("Could not save module-yield checkpoint; job stopped"); }
    }
    public boolean ownsStorageMenu(){return armed && loop.active() && ownsScreen;}
    public String backgroundStatus(){return "Background: "+(keepRunningInMenus()?"enabled":"idle")+" | window "+(mc.isWindowActive()?"focused":"unfocused")+" | owned menu "+ownsScreen+" | server snapshots "+fullSnapshotRevision;}
    public boolean permitContainerClick() {
        boolean permitted=(!miningTools.swapping() || ownClick) && Cooperation.allowContainerClick(cooperativeJob(), ownsScreen, before != null, ownClick);
        ownClick=false; // consume the one authorized click before any reentrant module callback
        return permitted;
    }
    @EventHandler(priority = EventPriority.LOWEST)
    private void afterToolSelection(StartBreakingBlockEvent event) {
        if ((armed || miningTools.active()) && !permitBreak(event.blockPos)) event.cancel();
    }
    @Override public void safety() {
        require(mc.player != null && mc.level != null && mc.player.isAlive(), "Player died or world closed");
        require(!interrupted && connection == mc.getConnection() && Policy.sameScope(profile.scope,scope()), "Reconnect or dimension change; run #mine again");
        require(lastPosition == null || lastPosition.distanceToSqr(mc.player.position()) <= 4096, "Large unexpected displacement; run #mine again after checking your position");
        lastPosition = mc.player.position();

    }
    private boolean durable(ItemStack stack) {
        if(miningTools.active())return MiningTools.healthy(stack);
        if(profile==null)return MiningTools.healthy(stack);
        return !stack.isDamageableItem() || Policy.healthy(stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage(),profile.durabilityReserve,profile.durabilityPercent);
    }
    private boolean usableTool(ItemStack stack) {
        if (!durable(stack)) return false;
        AutoTool tool = Modules.get().get(AutoTool.class);
        return tool == null || !tool.isActive() || !((AutoToolAccess) tool).autostore$shouldStopUsing(stack);
    }
    public boolean safeSpare(ItemStack stack) {return MiningTools.healthy(stack) && usableTool(stack);}
    public boolean protectedToolSlot(int slot) {return profile!=null && profile.protectedSlots.contains(slot);}
    public boolean toolActivityBlocked() {
        if(mc.player==null || manuallyPaused || clearing() && baritone().getBuilderProcess().isPaused() || mc.player.isUsingItem() || eatingRequested() || ownsScreen || before!=null)return true;
        KillAura aura=Modules.get().get(KillAura.class);
        AutoGap gap=Modules.get().get(AutoGap.class);
        return gap!=null && gap.isEating() || aura!=null && aura.isActive() && aura.attacking || now()<combatUntil
            || cooperativeJob() && loop.state!=Loop.State.MINING && loop.state!=Loop.State.RETURNING;
    }
    public boolean prepareMiningTool(net.minecraft.world.level.block.state.BlockState block) {
        return !miningTools.active() || !toolActivityBlocked() && miningTools.require(block);
    }
    public void toolInventorySwap(int source,int hotbar) {
        ownClick=true;
        try {mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,source,hotbar,ContainerInput.SWAP,mc.player);
            mc.getConnection().send(ContainerRefresh.packet(mc.player.inventoryMenu.containerId));}
        finally {ownClick=false;}
    }
    public boolean reserveToolBreak(BlockPos target){
        if(mc.level==null)return false;
        var block=mc.level.getBlockState(target);
        if(!miningTools.reserveBreak(block))return false;
        return true;
    }
    public void observeBreakAttempt(BlockPos target) {
        if(mc.level!=null)recovery.attempted(target,mc.level.getBlockState(target));
    }
    public boolean permitBreak(BlockPos target) {
        if(miningTools.active() && (mc.level==null || !prepareMiningTool(mc.level.getBlockState(target))))return false;
        if (!cooperativeJob()) return true;
        if (interrupted || mc.player == null || mc.level == null || busy()) return false;
        if(clearing() && !profile.mayTarget(pos(target)))return false;
        if (loop.state != Loop.State.MINING && loop.state != Loop.State.RETURNING) return false;
        if(loop.state==Loop.State.MINING) return !nearlyFull(); // native Baritone chooses tools and break targets
        if (!profile.mayTarget(pos(target))) return false;
        // Native Baritone may break access blocks while mining or travelling.
        ItemStack held = mc.player.getMainHandItem();
        if (!durable(held)) return false;
        return true;
    }
    public void broken() { if (cooperativeJob()) broken++; }
    public boolean permitTarget(BlockPos target) { return !(storageRouting() || clearing() && cooperativeJob()) || profile != null && profile.mayTarget(pos(target)); }
    /** Optional Xaero support without a compile-time or runtime dependency on the map mod. */
    private static boolean xaeroMapScreen(net.minecraft.client.gui.screens.Screen screen) {
        return xaeroMapClass(screen==null?null:screen.getClass());
    }
    private static boolean xaeroMapClass(Class<?> screenClass) {
        for(Class<?> type=screenClass;type!=null;type=type.getSuperclass())
            if(type.getName().equals("xaero.map.gui.GuiMap"))return true;
        return false;
    }
    public static boolean passiveScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
            || screen instanceof net.minecraft.client.gui.screens.PauseScreen || xaeroMapScreen(screen);
    }
    private boolean externalScreen() {
        var screen=mc.gui.screen();
        if(!ownsScreen && Cooperation.needsStorageMenu(loop.state) && !mc.player.inventoryMenu.getCarried().isEmpty())return true;
        return Cooperation.screenBlocksJob(loop.state,ownsScreen,screen!=null,
            passiveScreen(screen),
            screen instanceof net.minecraft.client.gui.screens.ChatScreen,
            mc.player.containerMenu!=mc.player.inventoryMenu);
    }
    /** Called only after KillAura has selected an eligible target, before its weapon check. */
    public void beforeAuraAttack() {
        if (!cooperativeJob() || mc.player == null || mc.player.isUsingItem() || eatingRequested()) return;
        for (int i=0; i<9; i++) {
            ItemStack weapon=mc.player.getInventory().getItem(i);
            if (weapon.is(net.minecraft.tags.ItemTags.SWORDS) && Policy.healthy(weapon.getMaxDamage()-weapon.getDamageValue(),weapon.getMaxDamage(),profile.durabilityReserve,profile.durabilityPercent)) {
                if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
                baritone().getInputOverrideHandler().clearAllKeys();
                if(combatReturnSlot<0) combatReturnSlot=mc.player.getInventory().getSelectedSlot();
                combatSwordSlot=i;
                mc.player.getInventory().setSelectedSlot(i);
                onCombatAttack();
                return;
            }
        }
    }
    private boolean special(ItemStack s) {
        return !s.getComponentsPatch().isEmpty() || s.isDamageableItem() || s.has(DataComponents.CUSTOM_NAME) || s.isEnchanted() || s.has(DataComponents.FOOD)
            || s.has(DataComponents.EQUIPPABLE) || s.has(DataComponents.CONTAINER) || s.getItem() instanceof BlockItem b && (b.getBlock() instanceof ShulkerBoxBlock || b.getBlock() == Blocks.ENDER_CHEST)
            || s.is(Items.FIREWORK_ROCKET) || s.is(Items.TOTEM_OF_UNDYING);
    }
    private Policy.Stack stack(ItemStack s) { return new Policy.Stack(itemId(s),s.getCount(),s.getMaxStackSize(),special(s)); }
    private List<Policy.Stack> inventoryStacks() {
        List<Policy.Stack> all=new ArrayList<>();
        for(int i=0;i<36;i++)all.add(stack(mc.player.getInventory().getItem(i)));
        return all;
    }
    private boolean depositable(ItemStack s,int slot) {
        return Policy.depositable(stack(s),slot,profile.outputs,profile.protectedItems,profile.protectedSlots)
            && (!clearing() || clearInventory.mayDeposit(stack(s),inventoryStacks(),profile.protectedSlots));
    }
    private boolean allowed(ItemStack s,int inventorySlot,Profile.Depot depot) {
        return depositable(s,inventorySlot)
            && (depot.outputs.isEmpty() || depot.outputs.contains(itemId(s)));
    }
    @Override public boolean nearlyFull() {
        List<Policy.Stack> inventory = inventoryStacks();
        if(clearing()) {
            int empty=0;
            for(int i=0;i<36;i++)if(!profile.protectedSlots.contains(i) && inventory.get(i).empty())empty++;
            return empty<=profile.reserveSlots;
        }
        for (String id : profile.outputs) {
            ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
            int capacity = Policy.capacity(inventory,id,sample.getMaxStackSize(),profile.protectedSlots);
            if (Policy.nearlyFull(capacity,sample.getMaxStackSize(),profile.reserveSlots,0)) return true;
        }
        return false;
    }
    @Override public boolean miningActive() { return toolJobActive(); }
    private <T> void enforce(Settings.Setting<T> setting,T value) {
        oldSettings.putIfAbsent(setting,setting.value); setting.value=value; enforced.put(setting,value);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private void releaseSettings() {
        oldSettings.forEach((s,v) -> ((Settings.Setting)s).value=v); oldSettings.clear(); enforced.clear();
    }
    private void travelSettings() {
        Settings s=BaritoneAPI.getSettings();
        // Only the temporary storage trip changes navigation settings; native mining settings are restored.
        enforce(s.allowBreak,true); enforce(s.allowPlace,true); enforce(s.allowInventory,true);

        enforce(s.itemSaver,true); enforce(s.itemSaverThreshold,profile.durabilityReserve);
        enforce(s.disconnectOnArrival,false);
        enforce(s.censorCoordinates,true); enforce(s.chatDebug,false);
    }
    @Override public void resumeMining() {
        travelGoal=null;
        releaseSettings();
        enforce(BaritoneAPI.getSettings().censorCoordinates,true);
        restoreUserScreen();
        // MineProcess is still active. The temporary process releases control when the loop returns to MINING.
    }
    @Override public void cancel() {
        travelGoal=null;
        ((baritone.Baritone)baritone()).getPathingBehavior().cancelSegmentIfSafe();
        baritone().getInputOverrideHandler().clearAllKeys();
        if(mc.gameMode!=null) mc.gameMode.stopDestroyBlock();
    }
    @Override public void stop() {
        travelGoal=null;
        baritone().getPathingBehavior().cancelEverything();
        baritone().getInputOverrideHandler().clearAllKeys();
        if(mc.gameMode!=null) mc.gameMode.stopDestroyBlock();
    }
    @Override public boolean stopped() { return !baritone().getPathingBehavior().isPathing() && !baritone().getPathingBehavior().hasPath() && baritone().getPathingBehavior().getInProgress().isEmpty(); }
    @Override public int chooseDepot(Set<Integer> attempted) {
        // Do not reject a saved destination from a distant block lookup or an inventory guess.
        // Travel there first, then verify the actual container and available space.
        return depotSelector.nearest(profile.depots,pos(mc.player.blockPosition()),attempted,now());
    }
    @Override public void storageFailed(int depot,String reason) {
        profile.depots.get(depot).status=reason; save();
    }
    @Override public String storageWaitReason() {
        int count=profile.depots.size();
        if(!hasDepositOutput()) return "No unprotected mined items to deposit; inventory needs space. "+count+" storage locations saved; retrying in 30 seconds";
        return "No usable storage on this pass ("+count+" saved). #store list shows each result; "
            + (nativeJob==NativeJob.MINE && mc.getCurrentServer()!=null && mc.getSingleplayerServer()==null
                ? "relog recovery will retry when eligible":"retrying in 30 seconds");
    }
    @Override public void depotUnavailable(int depot) { depotSelector.unavailable(depot,now()); }
    @Override public boolean travelFailed() {
        // Reaching an old registration spot is not arrival if the chest cannot be opened there.
        return pathFailed || travelGoal!=null && travelGoal.isInGoal(baritone().getPlayerContext().playerFeet()) && !arrived(travellingDepot);
    }
    @Override public long travelProgress() {
        BlockPos current=mc.player.blockPosition();
        if(!current.equals(lastTravelProgressPosition) || broken!=lastTravelBroken) {
            lastTravelProgressPosition=current; lastTravelBroken=broken; travelProgressCounter++;
        }
        return travelProgressCounter;
    }
    @Override public void travel(int depot) {
        pathFailed=false; lastTravelProgressPosition=null;
        storageFailed(depot,"PATHING");
        travelSettings(); travellingDepot=depot;alternateApproach=false;failedApproaches.clear();
        travelGoal=new GoalBlock(pos(profile.depots.get(depot).stand));
        // storageProcess supplies this goal temporarily; the native miner is not cancelled.
    }
    @Override public boolean retryTravel(int depot) {
        if(before!=null)return false; // an unresolved transfer never changes its navigation policy
        BlockPos feet=baritone().getPlayerContext().playerFeet();
        if(alternateApproach && (travelGoal==null || !travelGoal.isInGoal(feet)))return false;
        Profile.Depot d=profile.depots.get(depot);
        if(mc.level.hasChunkAt(pos(d.position)) && !depotValid(d))return false;
        failedApproaches.add(alternateApproach?pos(feet):d.stand);
        var candidates=DepotApproach.alternatives(d.position,d.otherHalf,failedApproaches);
        if(candidates.isEmpty())return false;
        cancel();pathFailed=false;alternateApproach=true;lastTravelProgressPosition=null;
        travelGoal=new GoalComposite(candidates.stream().map(p->new GoalBlock(pos(p))).toArray(Goal[]::new));
        storageFailed(depot,"TRYING_ANOTHER_APPROACH");return true;
    }
    @Override public void travelForConfirmation(int depot) {
        travel(depot);
        // Reconciliation must not consume/place/break inventory while its transfer is unresolved.
        enforce(BaritoneAPI.getSettings().allowBreak,false);enforce(BaritoneAPI.getSettings().allowPlace,false);
    }
    @Override public boolean arrived(int depot) {
        Profile.Depot d=profile.depots.get(depot);
        return depotValid(d) && reachableHit(d) != null;
    }
    private BlockHitResult reachableHit(Profile.Depot d) {
        Vec3 eye=mc.player.getEyePosition();
        for(Policy.Pos target:d.otherHalf==null?List.of(d.position):List.of(d.position,d.otherHalf)) {
            BlockPos p=pos(target);
            if(eye.distanceToSqr(Vec3.atCenterOf(p))>=20)continue;
            BlockHitResult hit=mc.level.clip(new ClipContext(eye,Vec3.atCenterOf(p),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK && (hit.getBlockPos().equals(pos(d.position))
                || d.otherHalf!=null && hit.getBlockPos().equals(pos(d.otherHalf))))return hit;
        }
        return null;
    }
    private Policy.Pos otherHalf(BlockPos p) {
        var state=mc.level.getBlockState(p);
        if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) return pos(ChestBlock.getConnectedBlockPos(p,state));
        return null;
    }
    private boolean depotValid(Profile.Depot d) {
        BlockPos p=pos(d.position);
        if(!mc.level.hasChunkAt(p)) return false;
        var state=mc.level.getBlockState(p);
        if(!blockId(state.getBlock()).equals(d.block)
            || !(state.getBlock()==Blocks.CHEST || state.getBlock() instanceof ShulkerBoxBlock))return false;
        Policy.Pos currentHalf=otherHalf(p);
        if(!Objects.equals(currentHalf,d.otherHalf)) {
            if(before!=null)return false; // do not reinterpret an in-flight transfer's slot layout
            d.otherHalf=currentHalf;d.size=currentHalf==null?27:54;
            d.lastObserved.clear();d.lastEmptySlots=0;depotSelector.clear();save();
        }
        return true;
    }
    public int registeredCount() { if(profile==null) load(); return profile.depots.size(); }
    public int clearDepots(String id) {
        manualStop(); editable();
        int beforeCount=profile.depots.size();
        if(id==null) profile.depots.clear(); else profile.depots.removeIf(d -> d.id.equals(id));
        depotSelector.clear(); save();
        return beforeCount-profile.depots.size();
    }
    public void addDepot() {
        editable(); int n=1;
        Set<String> names=new HashSet<>(); profile.depots.forEach(d -> names.add(d.id));
        while(names.contains("storage"+n)) n++;
        addDepot("storage"+n);
    }
    public void addDepot(String id) {
        editable(); require(id.matches("[a-zA-Z0-9_-]{1,32}"),"Use a short depot ID with letters, digits, underscore or hyphen");
        require(mc.hitResult instanceof BlockHitResult,"Look directly at a placed chest or shulker");
        BlockPos p=((BlockHitResult)mc.hitResult).getBlockPos();
        Block b=mc.level.getBlockState(p).getBlock();
        require(b==Blocks.CHEST || b instanceof ShulkerBoxBlock,"Only ordinary chests and placed shulkers are supported");
        Profile.Depot d=new Profile.Depot(); d.id=id; d.position=pos(p); d.otherHalf=otherHalf(p); d.stand=pos(baritone().getPlayerContext().playerFeet()); d.block=blockId(b); d.size=d.otherHalf==null?27:54;
        require(reachableHit(d) != null,"Stand where the chest or placed shulker can be opened");
        Profile.Depot existing=profile.depots.stream().filter(x -> x.position.equals(d.position) || Objects.equals(x.position,d.otherHalf) || Objects.equals(x.otherHalf,d.position)).findFirst().orElse(null);
        if (existing!=null) { existing.stand=d.stand; save(); return; }
        require(profile.depots.size()<32,"At most 32 containers can be registered");
        require(profile.depots.stream().noneMatch(x -> x.id.equals(id)),"Storage name already used");
        require(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))<20,"Register from a reachable standing position");
        profile.depots.add(d); save();
    }
    @Override public boolean canOpenStorage() {
        if(mc.player.containerMenu!=mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty())return false;
        var screen=mc.gui.screen();
        return screen==null || passiveScreen(screen);
    }
    @Override public boolean open(int depot) {
        Profile.Depot d=profile.depots.get(depot);
        if (!depotValid(d)) { d.status="MISSING_OR_CHANGED";save();return false; }
        require(canOpenStorage(),"Unexpected screen or held cursor stack before opening storage");
        var screen=mc.gui.screen();
        if(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) restoreScreenKind=1;
        else if(screen instanceof net.minecraft.client.gui.screens.PauseScreen pause) {restoreScreenKind=2;restorePauseMenu=pause.showsPauseMenu();}
        else if(xaeroMapScreen(screen)) {restoreScreenKind=3;restoreMapScreen=screen;}
        if(screen!=null)mc.gui.setScreen(null);
        BlockHitResult hit=reachableHit(d);
        if(hit==null) {storageFailed(depot,"BLOCKED_OR_OUT_OF_REACH");return false;}
        expectedDepot=depot; snapshot=null; ownsScreen=true;snapshotWaitSince=-1;
        ServerInteractions.face(mc,hit.getLocation());
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
        return true;
    }
    /** Called AFTER vanilla applies a full server content packet on the client thread. */
    public void onSnapshot(ClientboundContainerSetContentPacket packet) {
        miningTools.onInventorySnapshot(packet);
        if(!armed || !loop.active() || !ownsScreen || mc.player==null || packet.containerId()==0 || packet.containerId()==lastClosedId) return;
        if(packet.containerId()!=mc.player.containerMenu.containerId) return;
        snapshot=new Snapshot(packet.containerId(),packet.items().stream().map(ItemStack::copy).toList());snapshotRevision++;fullSnapshotRevision=snapshotRevision;
    }
    public void onSlotSnapshot(ClientboundContainerSetSlotPacket packet) {
        if(!ownsScreen || snapshot==null || mc.player==null || packet.getContainerId()!=snapshot.id()
            || packet.getContainerId()!=mc.player.containerMenu.containerId || packet.getSlot()<0 || packet.getSlot()>=snapshot.items().size())return;
        List<ItemStack> items=new ArrayList<>(snapshot.items());items.set(packet.getSlot(),packet.getItem().copy());
        snapshot=new Snapshot(snapshot.id(),List.copyOf(items));snapshotRevision++;
    }
    private boolean snapshotMatchesMenu() {
        if(snapshot==null || snapshot.id()!=mc.player.containerMenu.containerId || snapshot.items().size()!=mc.player.containerMenu.slots.size())return false;
        for(int i=0;i<snapshot.items().size();i++) {
            var slot=mc.player.containerMenu.slots.get(i);
            int playerSlot=slot.container==mc.player.getInventory()?slot.getContainerSlot():-1;
            if(!unchangedOrCombatWear(snapshot.items().get(i),slot.getItem(),playerSlot))return false;
        }
        return true;
    }
    @Override public boolean transferPending(){return before!=null;}
    @Override public boolean inlineConfirmation(){return true;}
    @Override public boolean ready(int depot) {
        if(mc.player.containerMenu==mc.player.inventoryMenu) return false;
        if(expectedDepot!=depot || !ownsScreen)return false;
        Profile.Depot d=profile.depots.get(depot); AbstractContainerMenu menu=mc.player.containerMenu;
        if(!depotValid(d))return false;
        if(!(d.block.equals("minecraft:chest") && menu instanceof ChestMenu)
            && !(!d.block.equals("minecraft:chest") && menu instanceof ShulkerBoxMenu))return false;
        if(menu.slots.size()!=d.size+36 || !menu.getCarried().isEmpty())return false;
        for(int i=d.size;i<menu.slots.size();i++)if(menu.slots.get(i).container!=mc.player.getInventory())return false;
        if(!snapshotMatchesMenu()) {requestStorageRefresh();return false;}
        snapshotWaitSince=-1;
        d.lastObserved.clear(); d.lastEmptySlots=0;
        for(int i=0;i<d.size;i++) { ItemStack s=snapshot.items().get(i); if(s.isEmpty()) d.lastEmptySlots++; else d.lastObserved.merge(itemId(s),s.getCount(),Integer::sum); }
        boolean hasRoom=false;
        for (int i=0;i<d.size && !hasRoom;i++) {
            ItemStack stored=snapshot.items().get(i);
            if (stored.isEmpty()) hasRoom=true;
            else if (profile.outputs.contains(itemId(stored)) && (d.outputs.isEmpty() || d.outputs.contains(itemId(stored)))
                && !special(stored) && stored.getCount()<Math.min(stored.getMaxStackSize(),menu.slots.get(i).getMaxStackSize(stored))) hasRoom=true;
        }
        depotSelector.observeCapacity(depot,hasRoom,now());
        d.observedAt=System.currentTimeMillis(); d.status=hasRoom?"HAS_ROOM":"FULL_FOR_TARGET"; save();
        return true;
    }
    private void requestStorageRefresh() {
        long now=now();
        if(snapshotWaitSince<0)snapshotWaitSince=now;
        if(ownsScreen && mc.player.containerMenu!=mc.player.inventoryMenu && now-lastSnapshotRequest>=1_000) {
            lastSnapshotRequest=now;mc.getConnection().send(ContainerRefresh.packet(mc.player.containerMenu.containerId));
        }
    }
    /** A fresh full reply is the new truth; never replay an old click or old stack count. */
    @Override public boolean rebaseTransfer() {
        if(before==null || !ownsScreen || mc.player.containerMenu==mc.player.inventoryMenu
            || !mc.player.containerMenu.getCarried().isEmpty() || !snapshotMatchesMenu()
            || fullSnapshotRevision<=pendingRevision || !ready(expectedDepot))return false;
        clearTransfer();
        profile.reason="Server inventory refreshed; continuing deposit";save();return true;
    }
    private void clearTransfer() {
        before=null;pendingClick=null;pendingSlot=-1;clickedPlayerSlots.clear();combatDamageSlots.clear();batchRejected=false;
    }
    @Override public void close() {
        if(ownsScreen && mc.player!=null && mc.player.containerMenu!=mc.player.inventoryMenu) {
            lastClosedId=mc.player.containerMenu.containerId; mc.player.closeContainer();
        }
        ownsScreen=false; snapshot=null;
    }
    /** Pace clicks by authoritative replies; keep ONE menu open for the entire load. */
    @Override public Loop.Click click(int depot) {
        if(expectedDepot!=depot || !ownsScreen || mc.player.containerMenu==mc.player.inventoryMenu)return Loop.Click.RECONCILE;
        Profile.Depot d=profile.depots.get(depot);var menu=mc.player.containerMenu;
        if(before!=null && menu.containerId!=before.id()) {
            ownsScreen=false;snapshot=null;return Loop.Click.RECONCILE; // never close a manually opened replacement container
        }
        if(pendingClick!=null) {
            if(fullSnapshotRevision<=pendingRevision || !snapshotMatchesMenu()) {
                if(now()-pendingSince>profile.openTimeoutMs)return Loop.Click.RECONCILE;
                return Loop.Click.PENDING;
            }
            Policy.Transfer result=compareSnapshots(pendingClick,snapshot,d.size,Set.of(pendingSlot));
            if(result==Policy.Transfer.INVALID)return Loop.Click.REBASE;
            pendingClick=null;pendingSlot=-1;
            if(result==Policy.Transfer.NO_CHANGE){batchRejected=true;return Loop.Click.SENT;}
        }
        if(!ready(depot)) {
            requestStorageRefresh();
            return now()-snapshotWaitSince>profile.openTimeoutMs?Loop.Click.RECONCILE:Loop.Click.PENDING;
        }
        // A paced load may take time on a slow server. Finish the confirmed prefix before
        // yielding for food/combat/manual pause; never strand a click in flight.
        if(before!=null && busy())return Loop.Click.SENT;
        boolean anyGlobalOutput=false;
        for(int i=d.size;i<menu.slots.size();i++) {
            ItemStack s=menu.slots.get(i).getItem();int playerSlot=menu.slots.get(i).getContainerSlot();
            if(depositable(s,playerSlot))anyGlobalOutput=true;
            if(!allowed(s,playerSlot,d))continue;
            int space=0;
            for(int j=0;j<d.size;j++) {
                ItemStack target=menu.slots.get(j).getItem();
                if(!menu.slots.get(j).mayPlace(s))continue;
                if(target.isEmpty())space+=Math.min(s.getMaxStackSize(),menu.slots.get(j).getMaxStackSize(s));
                else if(ItemStack.isSameItemSameComponents(s,target))space+=Math.max(0,Math.min(s.getMaxStackSize(),menu.slots.get(j).getMaxStackSize(s))-target.getCount());
            }
            if(space==0)continue;
            if(before==null) {
                before=snapshot;clickedPlayerSlots.clear();combatDamageSlots.clear();batchRejected=false;
                profile.reason="Deposit intent saved; restart requires manual reconciliation";save();
            }
            clickedPlayerSlots.add(i);pendingClick=snapshot;pendingSlot=i;pendingRevision=snapshotRevision;pendingSince=now();
            ownClick=true;
            try{mc.gameMode.handleContainerInput(menu.containerId,i,0,ContainerInput.QUICK_MOVE,mc.player);
                mc.getConnection().send(ContainerRefresh.packet(menu.containerId));}
            finally{ownClick=false;}
            return Loop.Click.PENDING;
        }
        if(before!=null)return Loop.Click.SENT;
        d.status=anyGlobalOutput?"FULL_OR_FILTERED":"OUTPUTS_DEPOSITED";save();
        return anyGlobalOutput?Loop.Click.FULL:Loop.Click.EMPTY;
    }
    @Override public Policy.Transfer verify() {
        require(before!=null && !clickedPlayerSlots.isEmpty() && snapshot!=null && (pendingClick==null || before.id()!=snapshot.id()),"Confirmed deposit snapshot required");
        Policy.Transfer result=compareSnapshots(before,snapshot,profile.depots.get(expectedDepot).size,clickedPlayerSlots);
        boolean rejected=batchRejected;
        if(result!=Policy.Transfer.INVALID)clearTransfer();
        return rejected && result==Policy.Transfer.MOVED?Policy.Transfer.NO_CHANGE:result;
    }
    private Policy.Transfer compareSnapshots(Snapshot oldSnapshot,Snapshot newSnapshot,int size,Set<Integer> clicked) {
        require(oldSnapshot.items().size()==newSnapshot.items().size(),"Container layout changed during transfer");
        List<ItemStack> identities=new ArrayList<>();
        List<BatchTransfer.Slot> oldSlots=new ArrayList<>(),newSlots=new ArrayList<>();
        for(int i=0;i<oldSnapshot.items().size();i++) {
            ItemStack old=oldSnapshot.items().get(i),current=newSnapshot.items().get(i);
            if(i>=size && !clicked.contains(i) && unchangedOrCombatWear(old,current,mc.player.containerMenu.slots.get(i).getContainerSlot()))current=old;
            oldSlots.add(batchSlot(old,identities));newSlots.add(batchSlot(current,identities));
        }
        return BatchTransfer.verify(oldSlots,newSlots,size,clicked);
    }
    private BatchTransfer.Slot batchSlot(ItemStack stack,List<ItemStack> identities) {
        if(stack.isEmpty())return new BatchTransfer.Slot(-1,0);
        for(int i=0;i<identities.size();i++)if(ItemStack.isSameItemSameComponents(stack,identities.get(i)))return new BatchTransfer.Slot(i,stack.getCount());
        identities.add(stack.copy());return new BatchTransfer.Slot(identities.size()-1,stack.getCount());
    }
    private boolean unchangedOrCombatWear(ItemStack old, ItemStack current, int inventorySlot) {
        if (ItemStack.matches(old,current)) return true;
        if (before == null || !old.isDamageableItem() || current.isEmpty()) return false;
        ItemStack adjusted=old.copy(); adjusted.setDamageValue(current.getDamageValue());
        return Cooperation.allowCombatWear(combatDamageSlots.contains(inventorySlot),ItemStack.matches(adjusted,current),
            old.getDamageValue(),current.getDamageValue(),old.getMaxDamage());
    }
    private void restoreUserScreen() {
        int kind=restoreScreenKind;restoreScreenKind=0;
        var map=restoreMapScreen;restoreMapScreen=null;
        if(kind==0 || mc.player==null || mc.level==null || mc.getConnection()!=connection || mc.gui.screen()!=null || mc.player.containerMenu!=mc.player.inventoryMenu)return;
        if(kind==1)mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        else if(kind==2)mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(restorePauseMenu));
        else if(map!=null)mc.gui.setScreen(map);
    }
    @Override public void checkpoint(Loop.State state,String reason) {
        if(state==Loop.State.PAUSED || state==Loop.State.FAULTED) {releaseSettings();if(!recovery.pending())sleepGuard.close();restoreUserScreen();}
        else if(state==Loop.State.WAITING_STORAGE) restoreUserScreen();
        if(profile!=null) { profile.checkpoint=state.name();profile.reason=reason;save(); }
    }
}
