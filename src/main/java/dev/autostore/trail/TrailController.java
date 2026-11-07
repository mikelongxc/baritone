package dev.autostore.trail;

import baritone.api.*;
import baritone.api.pathing.goals.GoalNear;
import baritone.process.elytra.ElytraBehavior;
import com.mojang.serialization.JsonOps;
import dev.autostore.*;
import dev.autostore.platform.IdleSleepGuard;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.nio.file.Path;
import java.util.*;

/** A single client-thread owner for flight and supply transactions. No UI automation or live-server assumptions. */
public final class TrailController {
    public static TrailController INSTANCE;
    public enum State { OFF, SEEKING, FLYING, LANDING, PREPARE, PLACING, OPENING, REFILLING, BREAKING, COLLECTING, WAITING, PAUSED }
    private final Minecraft mc=Minecraft.getInstance();
    private final Path directory=FabricLoader.getInstance().getConfigDir().resolve("autostore/trails");
    private final IdleSleepGuard sleep=new IdleSleepGuard();
    private TrailJournal journal;
    private TrailRoute route;
    private TrouserTrail detector;
    private State state=State.OFF;
    private String reason="Stopped";
    private boolean running,paused,stopAfterLanding,ownClick,ownsMenu,swapPending,transferPending;
    private int ticks,entered,lastPlan,lastProgress,lastRefresh,lastOpen,inventoryRevision,swapRevision,contentRevision,transferRevision;
    private int swapSource,swapDestination,beforeBox,beforePlayer,failures;
    private ItemStack expectedSource,expectedDestination;
    private AbstractContainerMenu ownedMenu;
    private List<ItemStack> contents;
    private int contentId=-1;
    private Screen restoreScreen;
    private BlockPos target,lastPosition;
    private Boolean confirmedBox;
    private boolean breaking,placementSent;
    private int placementInventoryRevision,lastTransfer,rejectedTransfers;
    private final RefuelTransaction transfer=new RefuelTransaction();
    private final Map<Settings.Setting<?>,Object> applied=new LinkedHashMap<>();
    private final Map<Settings.Setting<?>,Object> previous=new LinkedHashMap<>();
    private Object connection;
    private boolean interrupted;
    private long lastAnnounce;
    public boolean active(){return running;}
    public boolean background(){return running && state!=State.PAUSED;}
    public boolean deferEquipmentAutomation(){return inventoryLocked() && ticks-entered<40;}
    public boolean inventoryLocked(){return running && (swapPending || transferPending);}
    public boolean ownsSupplyMenu(){return running && ownsMenu;}
    public boolean controlsTool(){return running && switch(state){case PREPARE,PLACING,OPENING,REFILLING,BREAKING,COLLECTING->true;default->false;};}
    public boolean permitClick(){boolean allowed=!inventoryLocked() && !ownsMenu || ownClick;ownClick=false;return allowed;}
    private IBaritone bot(){return BaritoneAPI.getProvider().getPrimaryBaritone();}
    public String status(){return state+": "+reason+(mc.player==null?"":" | rockets "+rockets()+" | trail evidence "+(route==null?0:route.evidence().size()));}
    private void say(String message){ChatUtils.info("Trail: "+message);}
    private void state(State next,String message){state=next;reason=message;entered=ticks;if(System.currentTimeMillis()-lastAnnounce>2000){say(message);lastAnnounce=System.currentTimeMillis();}}
    public void start(boolean fresh)throws Exception {
        if(running){say(status());return;}
        if(mc.player==null || mc.level==null || mc.getConnection()==null)throw new IllegalStateException("Join a world first");
        if(!bot().getElytraProcess().isLoaded())throw new IllegalStateException("Native Elytra pathfinder is unavailable");
        if(Controller.INSTANCE==null)throw new IllegalStateException("AutoStore has not initialized");
        if(Controller.INSTANCE.cooperativeJob() || bot().getMineProcess().isActive() || bot().getBuilderProcess().isActive())
            throw new IllegalStateException("Stop the mining/building job before starting trail flight");
        detector=new TrouserTrail();detector.enable();
        journal=TrailJournal.load(directory,Controller.INSTANCE.recoveryScope());
        if(fresh && journal.hasBox())throw new IllegalStateException("Recover the placed supply shulker with #trail start before starting a new route");
        if(fresh || journal.evidence.isEmpty()) {
            double yaw=Math.toRadians(mc.player.getYRot());journal.headingX=-Math.sin(yaw);journal.headingZ=Math.cos(yaw);
            journal.evidence.clear();journal.visited.clear();journal.searches=0;journal.searchX=journal.searchZ=null;
        }
        route=new TrailRoute(journal.headingX,journal.headingZ);route.restore(journal.evidence,journal.visited);
        connection=mc.getConnection();interrupted=false;running=true;paused=false;stopAfterLanding=false;
        swapPending=transferPending=ownsMenu=false;transfer.clear();contents=null;ownedMenu=null;target=null;failures=0;
        bot().getPathingBehavior().cancelEverything();
        set(BaritoneAPI.getSettings().elytraAutoJump,true);
        set(BaritoneAPI.getSettings().elytraAllowEmergencyLand,true);
        set(BaritoneAPI.getSettings().disconnectOnArrival,false);
        set(BaritoneAPI.getSettings().allowInventory,true);
        set(BaritoneAPI.getSettings().allowBreak,false);
        set(BaritoneAPI.getSettings().allowPlace,false);
        set(BaritoneAPI.getSettings().censorCoordinates,true);
        set(BaritoneAPI.getSettings().chatDebug,false);
        set(BaritoneAPI.getSettings().elytraAutoSwap,true);
        set(BaritoneAPI.getSettings().elytraMinimumDurability,Math.max(44,BaritoneAPI.getSettings().elytraMinimumDurability.value));
        if(sleep.supported())sleep.start();
        lastPosition=mc.player.blockPosition();lastProgress=ticks;
        save();state(journal.hasBox()?State.OPENING:mc.player.onGround()?State.PREPARE:State.SEEKING,"Following Trouser Streak; supplies checked before each flight");
    }
    private <T> void set(Settings.Setting<T> setting,T value){previous.putIfAbsent(setting,setting.value);applied.put(setting,value);setting.value=value;}
    @SuppressWarnings({"unchecked","rawtypes"}) private void restoreSettings(){previous.forEach((s,v)->{if(Objects.equals(s.value,applied.get(s)))((Settings.Setting)s).value=v;});previous.clear();applied.clear();}
    public void stop() {stop(true);}
    private void stop(boolean cancelPath) {
        if(!running)return;
        running=false;paused=false;swapPending=transferPending=false;transfer.clear();
        if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();
        if(mc.player!=null && ownsMenu && mc.player.containerMenu==ownedMenu && ownedMenu.getCarried().isEmpty())mc.player.closeContainer();
        ownsMenu=false;ownedMenu=null;sleep.close();restoreSettings();
        try{save();}catch(Exception ignored){reason="Checkpoint write failed";}
        if(cancelPath)bot().getPathingBehavior().cancelEverything();state=State.OFF;reason="Stopped; #trail start resumes route and any placed supply box";
    }
    public void pause(){if(running){paused=true;stopAfterLanding=true;if(journal.hasBox() || transferPending || swapPending)reason="Finishing supply recovery before pause";else state(State.LANDING,"Landing to pause");}}
    public boolean resume(){if(!running || !paused)return false;paused=false;stopAfterLanding=false;if(sleep.supported())sleep.start();if(state==State.PAUSED || state==State.LANDING)state(journal.hasBox()?State.OPENING:State.SEEKING,"Resuming trail");return true;}
    @EventHandler private void left(GameLeftEvent e){interrupted=true;}
    @EventHandler(priority=EventPriority.LOWEST) private void tick(TickEvent.Post event) {
        ticks++;
        if(!running)return;
        try {
            if(interrupted || mc.player==null || mc.level==null || mc.getConnection()!=connection){stop();return;}
            if(!journal.scope.equals(Controller.INSTANCE.recoveryScope()) || !mc.player.isAlive()){stop();return;}
            if(Controller.INSTANCE.cooperativeJob() || bot().getMineProcess().isActive() || bot().getBuilderProcess().isActive()){stop(false);return;}
            if(state==State.PAUSED)return;
            if(swapPending){settleSwap();return;}
            if(!detector.active() && !journal.hasBox()){stopAfterLanding=true;state(State.LANDING,"Chunk detector disabled; landing");}
            if(mc.player.isUsingItem() && !transferPending)return;
            if(!mc.player.inventoryMenu.getCarried().isEmpty() || !mc.player.containerMenu.getCarried().isEmpty()){reason="Waiting for cursor stack to be put down";return;}
            if(mc.player.containerMenu!=mc.player.inventoryMenu && (!ownsMenu || mc.player.containerMenu!=ownedMenu)) {
                if(state==State.OPENING && mc.player.containerMenu instanceof ShulkerBoxMenu && ticks-lastOpen<100) {ownedMenu=mc.player.containerMenu;ownsMenu=true;}
                else{reason="Waiting for unrelated container";return;}
            }
            Screen screen=mc.gui.screen();
            if(screen!=null && !Controller.passiveScreen(screen) && !ownsMenu && !(screen instanceof net.minecraft.client.gui.screens.ChatScreen))return;
            if(ticks%20==0) {
                detector.sample(route,cell());route.visit(cell());
                if(lastPosition==null || lastPosition.distSqr(mc.player.blockPosition())>16){lastPosition=mc.player.blockPosition();lastProgress=ticks;}
            }
            if(ticks%200==0)save();
            switch(state) {
                case SEEKING,FLYING->fly();
                case LANDING->land();
                case PREPARE->prepare();
                case PLACING->place();
                case OPENING->open();
                case REFILLING->refill();
                case BREAKING->recoverBox();
                case COLLECTING->collect();
                case WAITING->waitForSuppliesOrTrail();
                default->{}
            }
        } catch(Exception failure) {
            // Do not print exception messages: game objects can stringify world locations.
            stop();say("Stopped after an internal/checkpoint error; route and supply-box recovery are retained where saved.");
        }
    }
    private TrailRoute.Cell cell(){return new TrailRoute.Cell(mc.player.blockPosition().getX()>>4,mc.player.blockPosition().getZ()>>4);}
    private void save()throws Exception {
        if(journal==null || route==null)return;
        journal.headingX=route.headingX;journal.headingZ=route.headingZ;journal.evidence=route.evidence();journal.visited=route.visited();journal.save(directory);
    }
    private void fly()throws Exception {
        ItemStack wings=mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if(FlightSupplies.land(rockets(),remaining(wings),wings.is(Items.ELYTRA)?wings.getMaxDamage():0)) {state(State.LANDING,"Landing before supplies reach reserve");return;}
        if(mc.player.onGround() && !FlightSupplies.launch(rockets(),remaining(wings),wings.getMaxDamage())){state(State.PREPARE,"Checking flight supplies");return;}
        if(ticks-lastProgress>400) {
            if(failures++==0){bot().getElytraProcess().resetState();lastProgress=ticks;reason="Refreshing stalled flight route";}
            else{stopAfterLanding=true;state(State.LANDING,"Flight is not progressing; landing instead of spending all rockets");}
            return;
        }
        if(target!=null && bot().getElytraProcess().isActive() && mc.player.position().distanceToSqr(Vec3.atCenterOf(target))>64*64)return;
        if(ticks-lastPlan<40)return;
        lastPlan=ticks;
        TrailRoute.Cell here=cell();Optional<TrailRoute.Cell> lead=route.next(here,14);
        TrailRoute.Cell next;
        if(lead.isPresent()) {
            next=lead.get();route.commit(here,next);journal.searches=0;journal.searchX=next.x();journal.searchZ=next.z();
            reason="Following previously visited chunks";
        } else {
            if(journal.searchX==null){journal.searchX=here.x();journal.searchZ=here.z();}
            if(journal.searches>=8){state(State.LANDING,"Trail lost after bounded search; landing to wait");return;}
            next=route.search(new TrailRoute.Cell(journal.searchX,journal.searchZ),journal.searches++);
            reason="Searching around the last trail lead ("+journal.searches+"/8)";
        }
        route.visit(next);save();
        int y=mc.player.blockPosition().getY();
        if(mc.player.onGround())y+=24;
        if(mc.level.dimension()==net.minecraft.world.level.Level.END)y=Math.max(y,100);
        int max=mc.level.getMaxY()-8;
        if(mc.level.dimension()==net.minecraft.world.level.Level.NETHER && !BaritoneAPI.getSettings().elytraAllowAboveRoof.value)max=Math.min(max,115);
        y=Math.max(mc.level.getMinY()+8,Math.min(y,max));
        target=new BlockPos(next.x()*16+8,y,next.z()*16+8);
        bot().getElytraProcess().pathTo(target);state=State.FLYING;entered=ticks;
    }
    private void land() {
        if(mc.player.onGround() && !mc.player.isFallFlying()) {
            bot().getPathingBehavior().cancelEverything();target=null;
            if(stopAfterLanding){state(State.PAUSED,"Paused on the ground; #trail resume to continue");paused=true;sleep.close();}
            else state(State.PREPARE,"Landed; checking supplies");
            return;
        }
        if(!bot().getElytraProcess().isActive())bot().getElytraProcess().pathTo(mc.player.blockPosition());
        bot().getElytraProcess().requestLanding();
        reason="Looking for solid landing terrain; retaining rocket reserve";
    }
    private void prepare()throws Exception {
        if(!mc.player.onGround()){state(State.LANDING,"Landing for refuel");return;}
        if(journal.hasBox()){state(State.OPENING,"Recovering the previous supply stop");return;}
        if(stopAfterLanding){state(State.PAUSED,"Paused after supply recovery; #trail resume to continue");sleep.close();return;}
        if(!passiveMenu())return;
        ItemStack wings=mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if(!wings.is(Items.ELYTRA) || FlightSupplies.land(Integer.MAX_VALUE,remaining(wings),wings.getMaxDamage())) {
            int spare=-1;
            for(int i=0;i<36;i++){ItemStack s=inv(i);if(s.is(Items.ELYTRA) && FlightSupplies.launch(64,remaining(s),s.getMaxDamage()) && (spare<0 || remaining(s)>remaining(inv(spare))))spare=i;}
            if(spare<0){state(State.WAITING,"Need a healthy Elytra in inventory");return;}
            if(spare>=9){swapToHotbar(spare);return;}
            swapInventory(6,spare);return; // vanilla chest armor menu slot -> hotbar SWAP
        }
        if(rockets()>=FlightSupplies.LAUNCH_ROCKETS) {
            if(journal.searches>=8 && route.next(cell(),14).isEmpty()){state(State.WAITING,"Trail ended; waiting for a lead or #trail new");return;}
            lastProgress=ticks;lastPosition=mc.player.blockPosition();state(State.SEEKING,"Supplied; resuming trail");return;
        }
        int box=-1;
        for(int i=0;i<36;i++)if(supplyBox(inv(i)) && boxRocketCount(inv(i))>0){box=i;break;}
        if(box<0){state(State.WAITING,"Need a fireworks-only shulker in inventory");return;}
        if(emptySlots()<1){state(State.WAITING,"Need one free inventory slot for rocket withdrawal and shulker recovery");return;}
        if(box>=9){swapToHotbar(box);return;}
        BlockPos spot=placementSpot();
        if(spot==null){state(State.WAITING,"No reachable solid refueling spot with space to open a shulker");return;}
        ItemStack stack=inv(box);
        journal.boxX=spot.getX();journal.boxY=spot.getY();journal.boxZ=spot.getZ();
        journal.boxType=Controller.itemId(stack);journal.boxIdentity=identity(stack);journal.boxRockets=boxRocketCount(stack);
        journal.boxesBefore=matchingBoxes();journal.placed=false;save();confirmedBox=null;placementSent=false;placementInventoryRevision=inventoryRevision;
        state(State.PLACING,"Placing the supply shulker");
    }
    private void waitForSuppliesOrTrail()throws Exception {if(ticks-entered>=100)state(State.PREPARE,"Retrying supplies and trail evidence");}
    private void place()throws Exception {
        if(Boolean.TRUE.equals(confirmedBox) && isOurBlock()) {journal.placed=true;save();state(State.OPENING,"Opening supply shulker");return;}
        if(isOurBlock() && ticks-entered>60){state(State.OPENING,"Confirming placed supply shulker");return;}
        if(ticks-entered>100) {
            // A rejected placement with the original box still in inventory is safe to retry.
            requestInventory();
            if(mc.level.getBlockState(boxPos()).isAir() && matchingBoxes()>=journal.boxesBefore && ticks-lastRefresh<2) {
                // Wait for a later authoritative inventory packet before retrying.
                if(inventoryRevision>placementInventoryRevision){journal.clearBox();save();state(State.WAITING,"Placement rejected; retrying a supply stop");}
            }
            return;
        }
        if(placementSent)return; // only one placement packet per intent
        int slot=findMatchingBox();if(slot<0){state(State.OPENING,"Reconciling supply placement");return;}
        if(slot>=9){swapToHotbar(slot);entered=ticks;return;}
        if(!passiveMenu())return;
        select(slot);BlockPos support=boxPos().below();Vec3 hit=new Vec3(support.getX()+0.5,support.getY()+1,support.getZ()+0.5);
        aim(hit);placementSent=true;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,Direction.UP,support,false));
    }
    private void open()throws Exception {
        if(!mc.player.onGround()){state(State.LANDING,"Landing before recovering the supply box");return;}
        if(!journal.hasBox()){state(State.PREPARE,"Supply box recovered");return;}
        if(!mc.level.hasChunkAt(boxPos())){reason="Waiting for supply chunk to load";return;}
        if(!isOurBlock()) {
            if(matchingBoxes()>=journal.boxesBefore){journal.clearBox();save();restoreMenu();state(State.PREPARE,"Supply box is in inventory");return;}
            if(!journal.placed && findMatchingBox()>=0){placementSent=false;placementInventoryRevision=inventoryRevision;state(State.PLACING,"Retrying unconfirmed supply placement");return;}
            state(State.COLLECTING,"Looking for the recovered supply shulker");return;
        }
        if(ownsMenu && mc.player.containerMenu==ownedMenu && ownedMenu instanceof ShulkerBoxMenu && contents!=null && contentId==ownedMenu.containerId) {
            if(!validSupplyContents()){stop();say("Supply box contains unexpected items; left it intact");return;}
            journal.placed=true;journal.boxRockets=containerRockets();save();state(State.REFILLING,"Withdrawing rockets in one open menu");return;
        }
        if(ticks-lastOpen<30)return;
        if(!passiveMenu())return;
        BlockHitResult hit=reachable(boxPos());
        if(hit==null){navigateNearBox();return;}
        lastOpen=ticks;contents=null;contentId=-1;ownsMenu=true;ownedMenu=null;
        aim(hit.getLocation());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
    }
    private void refill()throws Exception {
        if(!ownsMenu || mc.player.containerMenu!=ownedMenu || !(ownedMenu instanceof ShulkerBoxMenu)) {
            ownsMenu=false;ownedMenu=null;state(State.OPENING,"Reopening to reconcile supply transfer");return;
        }
        if(contents==null || contentId!=ownedMenu.containerId){requestContainer();return;}
        if(transferPending) {
            if(contentRevision<=transferRevision){if(ticks-lastRefresh>20)requestContainer();if(ticks-entered>100){closeOwned();state(State.OPENING,"Refreshing an unanswered supply transfer");}return;}
            if(!validSupplyContents()){stop();say("Supply contents changed; box left intact");return;}
            // A full server snapshot is the source of truth, including rejected/partial clicks.
            RefuelTransaction.Result result=transfer.snapshot(contentRevision,containerRockets(),rockets());
            if(result==RefuelTransaction.Result.WAIT)return;
            if(result==RefuelTransaction.Result.REJECTED)rejectedTransfers++;else rejectedTransfers=0;
            if(result==RefuelTransaction.Result.REBASED)reason="Rebased supply transfer from authoritative server contents";
            transferPending=false;journal.boxRockets=containerRockets();save();
            if(rejectedTransfers>=3){finishRefill();return;}
        }
        if(!validSupplyContents()){stop();say("Unexpected non-rocket supply contents; box left intact");return;}
        if(rockets()>=FlightSupplies.TARGET_ROCKETS || containerRockets()==0){finishRefill();return;}
        int source=-1;
        for(int i=0;i<27;i++)if(ElytraBehavior.isFireworks(contents.get(i)) && capacity(contents.get(i))>=contents.get(i).getCount()){source=i;break;}
        if(source<0){finishRefill();return;}
        if(mc.player.isUsingItem() || ticks-lastTransfer<10)return;
        beforeBox=containerRockets();beforePlayer=rockets();transferRevision=contentRevision;transferPending=true;entered=ticks;lastTransfer=ticks;
        journal.boxRockets=beforeBox;save();transfer.sent(transferRevision,beforeBox,beforePlayer);
        click(ownedMenu.containerId,source,0,ContainerInput.QUICK_MOVE);requestContainer();
    }
    private void finishRefill()throws Exception {
        journal.boxRockets=containerRockets();save();closeOwned();confirmedBox=null;breaking=false;state(State.BREAKING,"Recovering the supply shulker");
    }
    private void recoverBox()throws Exception {
        if(!isOurBlock()) {
            if(Boolean.FALSE.equals(confirmedBox)){mc.gameMode.stopDestroyBlock();state(State.COLLECTING,"Collecting supply shulker");requestInventory();}
            else if(ticks-entered>100){state(State.OPENING,"Reconciling supply block state");}
            return;
        }
        if(!passiveMenu())return;
        if(emptySlots()<1){reason="Waiting for one free slot to recover shulker";return;}
        BlockHitResult hit=reachable(boxPos());if(hit==null){navigateNearBox();return;}
        int tool=-1;
        for(int i=0;i<36;i++){ItemStack s=inv(i);if(s.is(ItemTags.PICKAXES) && MiningTools.healthy(s) && (tool<0 || s.getDestroySpeed(mc.level.getBlockState(boxPos()))>inv(tool).getDestroySpeed(mc.level.getBlockState(boxPos()))))tool=i;}
        if(tool<0){reason="Need a pickaxe above the 5% reserve to recover the supply box";return;}
        if(tool>=9){swapToHotbar(tool);return;}
        select(tool);aim(hit.getLocation());
        if(!breaking){mc.gameMode.startDestroyBlock(boxPos(),hit.getDirection());breaking=true;}
        else mc.gameMode.continueDestroyBlock(boxPos(),hit.getDirection());
        if(ticks-entered>200){mc.gameMode.stopDestroyBlock();breaking=false;entered=ticks;state(State.OPENING,"Retrying blocked supply-box recovery");}
    }
    private void collect()throws Exception {
        if(matchingBoxes()>=journal.boxesBefore) {
            journal.clearBox();save();bot().getPathingBehavior().cancelEverything();restoreMenu();state(State.PREPARE,"Supply shulker recovered; checking launch reserve");return;
        }
        if(isOurBlock()){state(State.OPENING,"Supply box is still placed; reconciling");return;}
        List<ItemEntity> drops=mc.level.getEntitiesOfClass(ItemEntity.class,new AABB(boxPos()).inflate(8),e->matchesBox(e.getItem()));
        BlockPos goal=drops.isEmpty()?boxPos():drops.getFirst().blockPosition();
        if(ticks%40==0){bot().getCustomGoalProcess().setGoalAndPath(new GoalNear(goal,0));requestInventory();}
        reason="Waiting for supply shulker pickup; never depart without it";
    }
    private boolean passiveMenu() {
        if(mc.player.containerMenu!=mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty())return false;
        Screen screen=mc.gui.screen();if(screen==null)return true;
        if(!Controller.passiveScreen(screen))return false;
        if(restoreScreen==null)restoreScreen=screen;mc.gui.setScreen(null);return true;
    }
    private void restoreMenu(){if(restoreScreen!=null && mc.gui.screen()==null){mc.gui.setScreen(restoreScreen);}restoreScreen=null;}
    private void closeOwned(){if(ownsMenu && mc.player.containerMenu==ownedMenu && ownedMenu.getCarried().isEmpty())mc.player.closeContainer();ownsMenu=false;ownedMenu=null;contents=null;contentId=-1;}
    private void navigateNearBox(){if(ticks%40==0)bot().getCustomGoalProcess().setGoalAndPath(new GoalNear(boxPos(),2));}
    private BlockPos boxPos(){return new BlockPos(journal.boxX,journal.boxY,journal.boxZ);}
    private boolean isOurBlock(){return journal.hasBox() && BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(boxPos()).getBlock()).toString().equals(journal.boxType);}
    private BlockPos placementSpot() {
        BlockPos feet=mc.player.blockPosition();
        for(int y=-1;y<=1;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            if(Math.abs(x)+Math.abs(z)<1)continue;
            BlockPos p=feet.offset(x,y,z);BlockPos support=p.below();
            if(!mc.level.getBlockState(p).isAir() || !mc.level.getBlockState(p.above()).isAir() || !mc.level.getFluidState(p).isEmpty())continue;
            if(!mc.level.getBlockState(support).isFaceSturdy(mc.level,support,Direction.UP) || !mc.level.getFluidState(support).isEmpty())continue;
            if(!mc.level.getEntities(mc.player,new AABB(p).inflate(0.05)).isEmpty() || new AABB(p).intersects(mc.player.getBoundingBox()))continue;
            Vec3 hit=new Vec3(p.getX()+0.5,p.getY(),p.getZ()+0.5);
            BlockHitResult ray=mc.level.clip(new ClipContext(mc.player.getEyePosition(),hit.add(0,-0.01,0),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(support) && ray.getDirection()==Direction.UP && mc.player.getEyePosition().distanceToSqr(hit)<16)return p;
        }
        return null;
    }
    private BlockHitResult reachable(BlockPos p) {
        for(Direction face:Direction.values()) {
            Vec3 center=Vec3.atCenterOf(p),point=center.add(face.getStepX()*0.499,face.getStepY()*0.499,face.getStepZ()*0.499);
            if(mc.player.getEyePosition().distanceToSqr(point)>16)continue;
            BlockHitResult hit=mc.level.clip(new ClipContext(mc.player.getEyePosition(),point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(p))return hit;
        }
        return null;
    }
    private void aim(Vec3 point) {ServerInteractions.face(mc,point);}
    private void select(int slot){mc.player.getInventory().setSelectedSlot(slot);mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}
    private ItemStack inv(int i){return mc.player.getInventory().getItem(i);}
    private int remaining(ItemStack stack){return stack.getMaxDamage()-stack.getDamageValue();}
    private int rockets(){int n=0;for(int i=0;i<36;i++)if(ElytraBehavior.isFireworks(inv(i)))n+=inv(i).getCount();return n;}
    private int emptySlots(){int n=0;for(int i=0;i<36;i++)if(inv(i).isEmpty())n++;return n;}
    private int capacity(ItemStack rocket) {
        int n=Math.max(0,emptySlots()-1)*rocket.getMaxStackSize(); // retain one recovery slot
        for(int i=0;i<36;i++)if(ItemStack.isSameItemSameComponents(inv(i),rocket))n+=Math.max(0,rocket.getMaxStackSize()-inv(i).getCount());
        return n;
    }
    private static boolean supplyBox(ItemStack stack) {
        if(!(stack.getItem() instanceof BlockItem b) || !(b.getBlock() instanceof ShulkerBoxBlock))return false;
        ItemContainerContents c=stack.get(DataComponents.CONTAINER);
        return c==null || c.nonEmptyItemCopyStream().allMatch(ElytraBehavior::isFireworks);
    }
    private static int boxRocketCount(ItemStack stack) {
        ItemContainerContents c=stack.get(DataComponents.CONTAINER);return c==null?0:c.nonEmptyItemCopyStream().filter(ElytraBehavior::isFireworks).mapToInt(ItemStack::getCount).sum();
    }
    private String identity(ItemStack stack) {
        ItemStack copy=stack.copyWithCount(1);copy.remove(DataComponents.CONTAINER);
        return canonical(ItemStack.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),copy).getOrThrow()).toString();
    }
    private static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
        if(value.isJsonObject()){var out=new com.google.gson.JsonObject();new java.util.TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->out.add(k,canonical(value.getAsJsonObject().get(k))));return out;}
        if(value.isJsonArray()){var out=new com.google.gson.JsonArray();value.getAsJsonArray().forEach(v->out.add(canonical(v)));return out;}
        return value;
    }
    private boolean matchesBox(ItemStack stack){return journal!=null && journal.boxType!=null && !stack.isEmpty() && Controller.itemId(stack).equals(journal.boxType) && identity(stack).equals(journal.boxIdentity) && supplyBox(stack);}
    private int matchingBoxes(){int n=0;for(int i=0;i<36;i++)if(matchesBox(inv(i)))n+=inv(i).getCount();return n;}
    private int findMatchingBox(){for(int i=0;i<36;i++)if(matchesBox(inv(i)) && boxRocketCount(inv(i))>0)return i;return -1;}
    private boolean validSupplyContents(){return contents!=null && contents.size()>=63 && contents.subList(0,27).stream().allMatch(s->s.isEmpty() || ElytraBehavior.isFireworks(s));}
    private int containerRockets(){return contents==null?0:contents.subList(0,Math.min(27,contents.size())).stream().filter(ElytraBehavior::isFireworks).mapToInt(ItemStack::getCount).sum();}
    private void swapToHotbar(int source){swapInventory(dev.autostore.core.ToolReserve.menuSlot(source),mc.player.getInventory().getSelectedSlot());}
    private void swapInventory(int sourceMenu,int hotbar) {
        if(!passiveMenu())return;
        swapSource=sourceMenu;swapDestination=dev.autostore.core.ToolReserve.menuSlot(hotbar);
        expectedSource=mc.player.inventoryMenu.getSlot(swapDestination).getItem().copy();expectedDestination=mc.player.inventoryMenu.getSlot(swapSource).getItem().copy();
        swapRevision=inventoryRevision;swapPending=true;entered=ticks;
        click(0,sourceMenu,hotbar,ContainerInput.SWAP);requestInventory();
    }
    private void settleSwap() {
        if(inventoryRevision>swapRevision) {
            boolean matched=ItemStack.matches(expectedSource,mc.player.inventoryMenu.getSlot(swapSource).getItem()) && ItemStack.matches(expectedDestination,mc.player.inventoryMenu.getSlot(swapDestination).getItem());
            swapPending=false;reason=matched?"Supply inventory synchronized":"Server rejected swap; retrying from fresh inventory";entered=ticks;return;
        }
        if(ticks-lastRefresh>20)requestInventory();
        reason="Waiting for server inventory confirmation";
    }
    private void click(int menu,int slot,int button,ContainerInput input){ownClick=true;try{mc.gameMode.handleContainerInput(menu,slot,button,input,mc.player);}finally{ownClick=false;}}
    private void requestInventory(){lastRefresh=ticks;mc.getConnection().send(ContainerRefresh.packet(0));}
    private void requestContainer(){lastRefresh=ticks;mc.getConnection().send(ContainerRefresh.packet(mc.player.containerMenu.containerId));}
    public void onSnapshot(ClientboundContainerSetContentPacket packet) {
        if(!running || mc.player==null)return;
        if(packet.containerId()==0)inventoryRevision++;
        if(ownsMenu && packet.containerId()!=0 && mc.player.containerMenu instanceof ShulkerBoxMenu && packet.containerId()==mc.player.containerMenu.containerId) {
            ownedMenu=mc.player.containerMenu;contentId=packet.containerId();contents=packet.items().stream().map(ItemStack::copy).toList();contentRevision++;
        }
    }
    public void onBlock(BlockPos p,BlockState state){if(running && journal!=null && journal.hasBox() && p.equals(boxPos()))confirmedBox=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(journal.boxType);}
}
