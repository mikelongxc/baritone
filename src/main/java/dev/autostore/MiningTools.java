package dev.autostore;

import baritone.api.process.*;
import baritone.utils.ToolSet;
import dev.autostore.core.ToolReserve;
import dev.autostore.core.ToolWearBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Tool-only pause/slot ownership. Never starts mining or changes its target/path goal. */
public final class MiningTools {
    private final Controller owner;
    private final Minecraft mc=Minecraft.getInstance();
    private boolean registered,enabled,waiting,manualPause,hardStopQueued;
    private long generation;
    private BlockState wanted;
    private int settleTicks,source=-1,destination=-1;
    private long swapStarted;
    private boolean swapConfirmed,swapRejected,checkingWear;
    private final ToolWearBudget wear=new ToolWearBudget();
    private long wearCheckStarted;
    private ItemStack expectedSource,expectedDestination;
    private String reason="";
    public MiningTools(Controller owner) {this.owner=owner;}
    public void begin() {
        reset();enabled=true;
        if(!registered) {
        owner.baritone().getPathingControlManager().registerProcess(new IBaritoneProcess() {
            public boolean isActive(){return active() && waiting();}
            public boolean isTemporary(){return true;}
            public double priority(){return 300;}
            public PathingCommand onTick(boolean failed,boolean safe) {
                owner.baritone().getInputOverrideHandler().clearAllKeys();
                return new PathingCommand(null,PathingCommandType.REQUEST_PAUSE);
            }
            public void onLostControl(){}
            public String displayName0(){return "AutoStore: protecting/replacing mining tool";}
        });
        registered=true;
        }
        if(mc.player!=null && mc.player.containerMenu==mc.player.inventoryMenu && mc.getConnection()!=null)checkWear();
    }
    public void reset(){wear.confirmed();checkingWear=false;generation++;hardStopQueued=false;swapConfirmed=swapRejected=false;manualPause=false;enabled=false;waiting=false;wanted=null;settleTicks=0;source=destination=-1;expectedSource=expectedDestination=null;reason="";}
    public void manualPause(boolean paused){manualPause=paused;}
    public boolean active(){return enabled && mc.player!=null && mc.level!=null && mc.player.isAlive() && !mc.player.isCreative() && owner.toolJobActive();}
    public boolean waiting(){return active() && (waiting || checkingWear || settleTicks>0);}
    public boolean swapping(){return active() && settleTicks>0;}
    public String reason(){return reason;}
    public String status(){
        if(mc.player==null)return "Tool guard: no player";
        ItemStack s=mc.player.getMainHandItem();
        return "Tool guard: "+(active()?"active":"inactive")+"; held "+Controller.itemId(s)
            +(s.isDamageableItem()?" "+(s.getMaxDamage()-s.getDamageValue())+"/"+s.getMaxDamage()+"; reserve "+ToolReserve.cutoff(s.getMaxDamage()):"")
            +(waiting()?"; "+reason:"");
    }
    public static boolean healthy(ItemStack s){
        int remaining=s.getMaxDamage()-s.getDamageValue();
        return !s.isDamageableItem() || ToolReserve.usable(remaining,s.getMaxDamage())
            && (long)remaining-wearCost(s)>=ToolReserve.cutoff(s.getMaxDamage());
    }
    private static int wearCost(ItemStack s){var tool=s.get(DataComponents.TOOL);return tool==null?1:Math.max(1,tool.damagePerBlock());}
    private static boolean needsTool(BlockState b) {
        return b.is(BlockTags.MINEABLE_WITH_SHOVEL) || b.is(BlockTags.MINEABLE_WITH_PICKAXE)
            || b.is(BlockTags.MINEABLE_WITH_AXE) || b.is(BlockTags.MINEABLE_WITH_HOE) || b.requiresCorrectToolForDrops();
    }
    private static boolean suitable(ItemStack s,BlockState b) {
        if(b.requiresCorrectToolForDrops() && !s.isCorrectToolForDrops(b))return false;
        if(!needsTool(b))return true;
        boolean familyMatches=b.is(BlockTags.MINEABLE_WITH_SHOVEL) && s.is(ItemTags.SHOVELS)
            || b.is(BlockTags.MINEABLE_WITH_PICKAXE) && s.is(ItemTags.PICKAXES)
            || b.is(BlockTags.MINEABLE_WITH_AXE) && s.is(ItemTags.AXES)
            || b.is(BlockTags.MINEABLE_WITH_HOE) && s.is(ItemTags.HOES);
        // A block can have multiple tool tags; shears/custom tools may also have explicit fast rules.
        return s.has(DataComponents.TOOL) && (familyMatches || s.getDestroySpeed(b)>1);
    }
    /** Called again at final destroyBlock, after other modules may have changed the held slot. */
    public boolean require(BlockState block) {
        if(!active())return true;
        if(hardStopQueued || checkingWear)return false;
        if(settleTicks>0)return false;
        ItemStack held=mc.player.getMainHandItem();
        if(belowFloor(held)){stopForWornTool(held);return false;}
        if(healthy(held) && suitable(held,block) && owner.safeSpare(held)) {
            wanted=null;waiting=false;reason="";return true;
        }
        wanted=block;waiting=true;return false;
    }
    public void tick() {
        try {update();}
        catch(RuntimeException failure) {
            owner.message("Tool replacement failed; mining stopped to protect equipment.");
            owner.manualStop();owner.baritone().getPathingBehavior().cancelEverything();reset();
        }
    }
    private void update() {
        if(!active()){if(enabled)reset();return;}
        if(hardStopQueued)return;
        if(checkingWear){
            if(owner.now()-wearCheckStarted>8_000)stopWithError("Server did not confirm tool durability; mining stopped.");
            return;
        }
        // A tick-level fallback catches wear even when another module bypasses a break callback.
        if(!waiting() && !owner.toolActivityBlocked() && !healthy(mc.player.getMainHandItem())) {
            ItemStack held=mc.player.getMainHandItem();
            var block=held.is(ItemTags.SHOVELS)?net.minecraft.world.level.block.Blocks.SAND:
                held.is(ItemTags.PICKAXES)?net.minecraft.world.level.block.Blocks.STONE:
                held.is(ItemTags.AXES)?net.minecraft.world.level.block.Blocks.OAK_LOG:
                held.is(ItemTags.HOES)?net.minecraft.world.level.block.Blocks.HAY_BLOCK:null;
            if(block!=null){if(!require(block.defaultBlockState()) && !active())return;}
        }
        if(hardStopQueued || !waiting())return;
        if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();
        if(settleTicks>0) {
            if(!swapConfirmed && !swapRejected && owner.now()-swapStarted<8_000)return;
            if(!swapConfirmed || swapRejected) {
                owner.message("Tool swap was not confirmed by the server; mining stopped to protect equipment.");
                owner.manualStop();owner.baritone().getPathingBehavior().cancelEverything();reset();return;
            }
            settleTicks=0;
            expectedSource=expectedDestination=null;source=destination=-1;
        }
        if(wanted==null){waiting=false;return;}
        if(manualPause || owner.toolActivityBlocked())return;
        if(mc.player.containerMenu!=mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty()) {
            explain("Tool replacement waiting: put down the cursor stack or close the other container.");return;
        }
        List<ToolReserve.Candidate> options=new ArrayList<>();
        for(int i=0;i<36;i++) {
            ItemStack s=mc.player.getInventory().getItem(i);
            options.add(new ToolReserve.Candidate(i,suitable(s,wanted),!owner.protectedToolSlot(i) && owner.safeSpare(s),
                s.getMaxDamage()-s.getDamageValue(),s.isDamageableItem()?s.getMaxDamage():0,
                ToolSet.calculateSpeedVsBlock(s,wanted)));
        }
        OptionalInt chosen=ToolReserve.choose(options);
        if(chosen.isEmpty()) {stopWithError("No suitable spare above 5% durability. Mining stopped; add a tool and restart the job.");return;}
        int slot=chosen.getAsInt();
        if(slot<9) {
            mc.player.getInventory().setSelectedSlot(slot);syncSelected();waiting=false;wanted=null;reason="";return;
        }
        int dest=-1;
        for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i) && suitable(mc.player.getInventory().getItem(i),wanted)
            && !healthy(mc.player.getInventory().getItem(i))){dest=i;break;}
        if(dest<0)for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i) && mc.player.getInventory().getItem(i).isEmpty()){dest=i;break;}
        if(dest<0 && !owner.protectedToolSlot(mc.player.getInventory().getSelectedSlot()))dest=mc.player.getInventory().getSelectedSlot();
        if(dest<0)for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i)){dest=i;break;}
        if(dest<0){explain("Mining paused: every hotbar slot is protected; free one for a spare tool.");return;}
        source=slot;destination=dest;
        expectedSource=mc.player.getInventory().getItem(dest).copy();
        expectedDestination=mc.player.getInventory().getItem(slot).copy();
        // SWAP needs no empty inventory slot: the worn tool occupies the spare's former slot.
        settleTicks=1;swapStarted=owner.now();swapConfirmed=swapRejected=false;
        owner.toolInventorySwap(ToolReserve.menuSlot(slot),dest);
        mc.player.getInventory().setSelectedSlot(dest);syncSelected();
        explain("Replacing mining tool; worn tools are kept in inventory.");
    }
    /** Called only at final local destruction, after ordinary ownership/safety checks. */
    public boolean reserveBreak(BlockState block) {
        if(!active())return true;
        if(!require(block))return false;
        ItemStack held=mc.player.getMainHandItem();
        if(!held.isDamageableItem())return true;
        int slot=mc.player.getInventory().getSelectedSlot(),cost=wearCost(held);
        if(wear.needsConfirmation(slot,held.getMaxDamage()-held.getDamageValue(),held.getMaxDamage(),cost)) {
            checkWear();return false;
        }
        wear.reserve(slot,cost);return true;
    }
    private void checkWear(){
        checkingWear=true;wearCheckStarted=owner.now();reason="Checking tool durability with the server";
        mc.getConnection().send(ContainerRefresh.packet(0));
    }
    public static boolean belowFloor(ItemStack stack){return stack.isDamageableItem() && ToolReserve.belowFloor(stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage());}
    public void stopForWornTool(ItemStack stack){
        stopWithError("Tool has "+(stack.getMaxDamage()-stack.getDamageValue())+"/"+stack.getMaxDamage()+" durability, below 5%. Mining stopped.");
    }
    private void stopWithError(String reason){
        if(hardStopQueued)return;
        hardStopQueued=true;waiting=true;this.reason="ERROR: "+reason;owner.message(this.reason);
        long ticket=generation;
        // Block this break immediately, then cancel outside the native builder/miner callback.
        mc.schedule(() -> {
            if(generation!=ticket || !hardStopQueued)return;
            owner.manualStop();owner.baritone().getPathingBehavior().cancelEverything();reset();
        });
    }
    private void syncSelected(){((meteordevelopment.meteorclient.mixininterface.IMultiPlayerGameMode)mc.gameMode).meteor$syncSelected();}
    public void onInventorySnapshot(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet) {
        if(packet.containerId()!=0)return;
        if(checkingWear){wear.confirmed();checkingWear=false;reason="";}
        if(!swapping())return;
        int a=ToolReserve.menuSlot(source),b=ToolReserve.menuSlot(destination);
        swapConfirmed=packet.items().size()>Math.max(a,b) && matchesWithWear(expectedSource,packet.items().get(a))
            && ItemStack.matches(expectedDestination,packet.items().get(b));
        swapRejected=!swapConfirmed;
        if(swapConfirmed)wear.confirmed();
    }
    private static boolean matchesWithWear(ItemStack expected,ItemStack actual) {
        if(ItemStack.matches(expected,actual))return true;
        if(expected==null || !expected.isDamageableItem() || actual.isEmpty() || actual.getDamageValue()<expected.getDamageValue()
            || actual.getDamageValue()>=expected.getMaxDamage())return false;
        ItemStack copy=expected.copy();copy.setDamageValue(actual.getDamageValue());return ItemStack.matches(copy,actual);
    }
    /** Keep native pickaxe preparation without repeatedly evicting the active shovel. */
    public int pickaxeRestockSlot() {
        for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i) && mc.player.getInventory().getItem(i).is(ItemTags.PICKAXES))return i;
        return supplyRestockSlot();
    }
    public int supplyRestockSlot() {
        for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i) && mc.player.getInventory().getItem(i).isEmpty())return i;
        for(int i=0;i<9;i++)if(!owner.protectedToolSlot(i) && i!=mc.player.getInventory().getSelectedSlot()
            && !mc.player.getInventory().getItem(i).has(DataComponents.TOOL))return i;
        return -1;
    }
    private void explain(String text){if(!text.equals(reason)){reason=text;owner.message(text);}}
}
