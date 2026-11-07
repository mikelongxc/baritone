import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Initialize an isolated loader and transform classes; never invoke Minecraft.main or game entrypoints. */
public final class LoaderSmoke {
    public static void main(String[] args) throws Exception {
        ClassLoader target = new Knot(EnvType.CLIENT).init(args);
        String[] classes = {
            "baritone.process.MineProcess",
            "baritone.process.MineGoals",
            "baritone.cache.FasterWorldScanner",
            "baritone.cache.TargetCooldown",
            "baritone.pathing.movement.Movement",
            "net.minecraft.client.multiplayer.MultiPlayerGameMode",
            "net.minecraft.client.multiplayer.ClientPacketListener",
            "meteordevelopment.meteorclient.systems.modules.player.AutoEat",
            "meteordevelopment.meteorclient.systems.modules.player.AutoTool",
            "meteordevelopment.meteorclient.systems.modules.combat.KillAura",
            "dev.autostore.AutoStoreAddon",
            "dev.autostore.Controller",
            "dev.autostore.MiningTools",
            "dev.autostore.MiningRecovery",
            "dev.autostore.core.DamageWatch",
            "dev.autostore.MiningRecovery$RecoveryScreen",
            "dev.autostore.ContainerRefresh",
            "baritone.process.BuilderProcess",
            "baritone.command.defaults.SelCommand",
            "baritone.command.defaults.ComeCommand",
            "com.mojang.blaze3d.platform.FramerateLimitTracker",
            "dev.autostore.MiningTools$1",
            "dev.autostore.Controller$1",
            "dev.autostore.Controller$2",
            "dev.autostore.StorageCommand",
            "dev.autostore.StoreCommand",
            "dev.autostore.trail.TrailController",
            "dev.autostore.trail.TrailCommand",
            "dev.autostore.trail.TrouserTrail",
            "net.minecraft.client.MouseHandler",
            "baritone.command.defaults.ExecutionControlCommands",
            "baritone.pathing.movement.MovementHelper",
            "baritone.behavior.InventoryBehavior",
            "baritone.api.utils.BlockOptionalMeta",
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.gui.Gui",
            "net.minecraft.client.gui.screens.PauseScreen",
            "net.minecraft.client.multiplayer.ClientChunkCache",
            "net.minecraft.client.multiplayer.ClientChunkCache$Storage",
            "net.minecraft.client.player.LocalPlayer",
            "net.minecraft.client.gui.components.CommandSuggestions",
            "net.minecraft.world.entity.Entity",
            "net.minecraft.client.renderer.entity.EntityRenderDispatcher",
            "net.minecraft.world.entity.projectile.FireworkRocketEntity",
            "net.minecraft.world.item.ItemStack",
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.level.storage.loot.LootContext$Builder",
            "net.minecraft.world.level.storage.loot.LootTable",
            "net.minecraft.network.Connection",
            "net.minecraft.world.level.chunk.PalettedContainer",
            "net.minecraft.world.level.chunk.PalettedContainer$Data",
            "net.minecraft.client.renderer.RenderPipelines",
            "net.minecraft.client.renderer.rendertype.RenderType",
            "net.minecraft.client.gui.screens.Screen",
            "net.minecraft.client.renderer.LevelRenderer"
        };
        for (String name : classes) {
            Class<?> type = Class.forName(name, false, target);
            type.getDeclaredMethods(); // Resolve signatures as well as applying Mixins.
            System.out.println("SMOKE_OK " + name);
        }
        if(Boolean.getBoolean("autostore.smoke.xaero")) {
            Class<?> map=Class.forName("xaero.map.gui.GuiMap",false,target);
            map.getDeclaredMethods();
            Class<?> controller=Class.forName("dev.autostore.Controller",false,target);
            var classify=controller.getDeclaredMethod("xaeroMapClass",Class.class);
            classify.setAccessible(true);
            if(!Boolean.TRUE.equals(classify.invoke(null,map)))throw new AssertionError("Xaero map must be passive");
            if(!Boolean.FALSE.equals(classify.invoke(null,Class.forName("net.minecraft.client.gui.screens.ChatScreen",false,target))))throw new AssertionError("Chat is not a replaceable map");
            System.out.println("SMOKE_OK installed Xaero map and XaeroPlus transformations; map classification");
        }
        Class<?> pathfinder = Class.forName("dev.babbaj.pathfinder.NetherPathfinder", true, target);
        Object supported=pathfinder.getMethod("isThisSystemSupported").invoke(null);
        // Match the vanilla bootstrap that precedes Baritone initialization; no client/world is constructed.
        Class.forName("net.minecraft.SharedConstants",true,target).getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap",true,target).getMethod("bootStrap").invoke(null);
        // Exercise real ItemStack durability methods with test-local component maps. The
        // client has not loaded datapack defaults, so ordinary default stacks are not bound yet.
        Class<?> stackType=Class.forName("net.minecraft.world.item.ItemStack",true,target);
        Class<?> itemType=Class.forName("net.minecraft.world.item.Item",true,target);
        Class<?> items=Class.forName("net.minecraft.world.item.Items",true,target);
        Class<?> material=Class.forName("net.minecraft.world.item.ToolMaterial",true,target);
        Class<?> mapType=Class.forName("net.minecraft.core.component.DataComponentMap",true,target);
        Class<?> builderType=Class.forName("net.minecraft.core.component.DataComponentMap$Builder",true,target);
        Class<?> patchedType=Class.forName("net.minecraft.core.component.PatchedDataComponentMap",true,target);
        Class<?> componentType=Class.forName("net.minecraft.core.component.DataComponentType",true,target);
        Class<?> components=Class.forName("net.minecraft.core.component.DataComponents",true,target);
        Class<?> holder=Class.forName("net.minecraft.core.Holder",true,target);
        var constructor=stackType.getDeclaredConstructor(holder,int.class,patchedType);constructor.setAccessible(true);
        var set=builderType.getMethod("set",componentType,Object.class);
        var healthy=Class.forName("dev.autostore.MiningTools",true,target).getMethod("healthy",stackType);
        var setDamage=stackType.getMethod("setDamageValue",int.class);
        String[][] materials={{"WOOD","WOODEN_SHOVEL"},{"STONE","STONE_SHOVEL"},{"COPPER","COPPER_SHOVEL"},{"IRON","IRON_PICKAXE"},{"GOLD","GOLDEN_AXE"},{"DIAMOND","DIAMOND_SHOVEL"},{"NETHERITE","NETHERITE_SHOVEL"}};
        for(String[] pair:materials) {
            int maximum=(int)material.getMethod("durability").invoke(material.getField(pair[0]).get(null));
            int cutoff=(int)Math.ceil(maximum*0.05);
            Object builder=mapType.getMethod("builder").invoke(null);
            set.invoke(builder,components.getField("MAX_DAMAGE").get(null),maximum);
            set.invoke(builder,components.getField("DAMAGE").get(null),maximum-cutoff);
            set.invoke(builder,components.getField("MAX_STACK_SIZE").get(null),1);
            Object map=builderType.getMethod("build").invoke(builder);
            Object registryHolder=itemType.getMethod("builtInRegistryHolder").invoke(items.getField(pair[1]).get(null));
            Object stack=constructor.newInstance(registryHolder,1,patchedType.getConstructor(mapType).newInstance(map));
            if(!Boolean.FALSE.equals(healthy.invoke(null,stack)))throw new AssertionError("Tool must be retired at five percent: "+pair[0]);
            setDamage.invoke(stack,maximum-cutoff-1);
            if(!Boolean.TRUE.equals(healthy.invoke(null,stack)))throw new AssertionError("Tool above reserve should be usable: "+pair[0]);
            if(pair[0].equals("NETHERITE")) {
                setDamage.invoke(stack,maximum-98);
                if(!Boolean.FALSE.equals(healthy.invoke(null,stack)))throw new AssertionError("98/2031 netherite must be retired");
            }
        }
        System.out.println("SMOKE_OK real ItemStack durability checks with synthetic components and all vanilla tool-material durability values");
        // Exercise the pinned vanilla no-slot click branch without constructing a client/world.
        Class<?> refresh=Class.forName("dev.autostore.ContainerRefresh",true,target);
        Object request=refresh.getMethod("packet",int.class).invoke(null,7);
        Class<?> packet=request.getClass();
        if((int)packet.getMethod("stateId").invoke(request)!=-1 || (short)packet.getMethod("slotNum").invoke(request)!=-1
            || (byte)packet.getMethod("buttonNum").invoke(request)!=0)throw new AssertionError("Refresh must be stale-state no-slot pickup");
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");var uf=unsafeClass.getDeclaredField("theUnsafe");uf.setAccessible(true);
        Object unsafe=uf.get(null);var allocate=unsafeClass.getMethod("allocateInstance",Class.class);
        Class<?> menuType=Class.forName("net.minecraft.world.inventory.AbstractContainerMenu",true,target);
        Class<?> inputType=Class.forName("net.minecraft.world.inventory.ContainerInput",true,target);
        Object menu=allocate.invoke(unsafe,Class.forName("net.minecraft.world.inventory.ChestMenu",true,target));
        Object fakePlayer=allocate.invoke(unsafe,Class.forName("net.minecraft.client.player.LocalPlayer",true,target));
        if(!Boolean.TRUE.equals(menuType.getMethod("isValidSlotIndex",int.class).invoke(menu,-1)))throw new AssertionError("No-slot click must be accepted");
        // Any attempt to access slots/cursor/inventory would fail on these zero-initialized objects.
        menuType.getMethod("clicked",int.class,int.class,inputType,Class.forName("net.minecraft.world.entity.player.Player",true,target))
            .invoke(menu,-1,0,inputType.getField("PICKUP").get(null),fakePlayer);
        System.out.println("SMOKE_OK actual vanilla no-slot pickup is a no-op; snapshot request fields");
        // Invoke the transformed renderFrame itself on a minimal in-memory fixture.
        // There is no GPU surface/window handle/client constructor/world/network session.
        // Reaching vanilla graphics code would fail on the null surface.
        Class<?> mcClass=Class.forName("net.minecraft.client.Minecraft",true,target);
        Class<?> controllerClass=Class.forName("dev.autostore.Controller",true,target);
        Object mcFixture=allocate.invoke(unsafe,mcClass),controllerFixture=allocate.invoke(unsafe,controllerClass);
        Object levelFixture=allocate.invoke(unsafe,Class.forName("net.minecraft.client.multiplayer.ClientLevel",true,target));
        var lightUpdates=new java.util.ArrayDeque<Runnable>();
        fixtureField(levelFixture,"lightUpdateQueue",lightUpdates);
        Object chunkCache=allocate.invoke(unsafe,Class.forName("net.minecraft.client.multiplayer.ClientChunkCache",true,target));
        fixtureField(chunkCache,"lightEngine",Class.forName("net.minecraft.world.level.lighting.LevelLightEngine",true,target).getField("EMPTY").get(null));
        fixtureField(levelFixture,"chunkSource",chunkCache);fixtureField(mcFixture,"level",levelFixture);
        Class<?> windowClass=Class.forName("com.mojang.blaze3d.platform.Window",true,target);
        Object windowFixture=allocate.invoke(unsafe,windowClass);
        fixtureField(mcFixture,"window",windowFixture);
        Object timerFixture=allocate.invoke(unsafe,Class.forName("net.minecraft.client.DeltaTracker$Timer",true,target));
        fixtureField(timerFixture,"msPerTick",50F);fixtureField(mcFixture,"deltaTracker",timerFixture);
        Object guiFixture=allocate.invoke(unsafe,Class.forName("net.minecraft.client.gui.Gui",true,target));
        Object toastFixture=allocate.invoke(unsafe,Class.forName("net.minecraft.client.gui.components.toasts.ToastManager",true,target));
        fixtureField(toastFixture,"visibleToasts",new java.util.ArrayList<>());
        fixtureField(toastFixture,"queued",new java.util.ArrayDeque<>());
        fixtureField(toastFixture,"playedToastSounds",new java.util.HashSet<>());
        fixtureField(guiFixture,"toastManager",toastFixture);fixtureField(mcFixture,"gui",guiFixture);
        Object loopFixture=allocate.invoke(unsafe,Class.forName("dev.autostore.core.Loop",true,target));
        Class<?> stateClass=Class.forName("dev.autostore.core.Loop$State",true,target);
        fixtureField(loopFixture,"state",stateClass.getField("DEPOSITING").get(null));
        fixtureField(controllerFixture,"loop",loopFixture);fixtureField(controllerFixture,"armed",true);
        fixtureField(controllerFixture,"recovery",allocate.invoke(unsafe,Class.forName("dev.autostore.MiningRecovery",true,target)));
        var instance=controllerClass.getField("INSTANCE");Object oldInstance=instance.get(null);instance.set(null,controllerFixture);
        try {
            var frame=mcClass.getMethod("renderFrame",boolean.class);
            for(String phase:new String[]{"MINING","QUIESCING","RETURNING","OPENING","DEPOSITING","REOPENING","VERIFYING","RESUMING","WAITING_STORAGE"}) {
                var executed=new java.util.concurrent.atomic.AtomicInteger();lightUpdates.add(executed::incrementAndGet);
                fixtureField(loopFixture,"state",stateClass.getField(phase).get(null));frame.invoke(mcFixture,true);
                if(executed.get()!=1 || !lightUpdates.isEmpty())throw new AssertionError("Background world updates were starved in "+phase);
            }
            fixtureField(loopFixture,"state",stateClass.getField("DEPOSITING").get(null));
            checkHeadlessMenu(target,unsafe,allocate,mcFixture,guiFixture,windowFixture,controllerFixture,fakePlayer,menu);
            checkTrailAdapter(target);
            fixtureField(windowFixture,"focused",true);
            expectNativeRender(frame,mcFixture); // focused bot must keep normal rendering
            fixtureField(windowFixture,"focused",false);fixtureField(controllerFixture,"manuallyPaused",true);
            expectNativeRender(frame,mcFixture); // explicit pause restores ordinary client behavior
            fixtureField(controllerFixture,"manuallyPaused",false);fixtureField(controllerFixture,"armed",false);
            expectNativeRender(frame,mcFixture); // no bot means no global rendering change
        } finally {instance.set(null,oldInstance);}
        System.out.println("SMOKE_OK actual transformed background frame bypass; foreground, pause and disarmed fall through normally");
        System.out.println("SMOKE_OK queued world lighting updates drain in every active phase without a GPU surface");
        checkMiningResume(target,unsafe,allocate);
        // Exercise real native goal construction after registry bootstrap, without a client/world.
        Class<?> goals=Class.forName("baritone.process.MineGoals",true,target);
        Class<?> blocks=Class.forName("net.minecraft.world.level.block.Blocks",true,target);
        Object sand=blocks.getField("SAND").get(null),red=blocks.getField("RED_SAND").get(null),ore=blocks.getField("DIAMOND_ORE").get(null);
        var surface=goals.getMethod("surfaceSand",boolean.class,java.util.List.class);
        if(!Boolean.TRUE.equals(surface.invoke(null,false,java.util.List.of(sand,red))))throw new AssertionError("Sand search policy");
        for(var inputs:java.util.List.of(java.util.List.of(ore),java.util.List.of(sand,ore),java.util.List.of()))
            if(!Boolean.FALSE.equals(surface.invoke(null,false,inputs)))throw new AssertionError("Non-sand search must stay native");
        if(!Boolean.FALSE.equals(surface.invoke(null,true,java.util.List.of(sand))))throw new AssertionError("Explicit legit mining policy");
        var nearby=goals.getMethod("nearbyMaterial",boolean.class,java.util.List.class);
        for(String bulkMaterial:java.util.List.of("SAND","RED_SAND","STONE","END_STONE","DEEPSLATE","NETHERRACK","BIRCH_LOG","OAK_LOG","SPRUCE_LOG","JUNGLE_LOG","ACACIA_LOG","DARK_OAK_LOG","MANGROVE_LOG","CHERRY_LOG","PALE_OAK_LOG","CRIMSON_STEM","WARPED_STEM")) {
            Object block=blocks.getField(bulkMaterial).get(null);
            if(!Boolean.TRUE.equals(nearby.invoke(null,false,java.util.List.of(block))))throw new AssertionError("Bulk material must use local elevation: "+bulkMaterial);
            if(!Boolean.FALSE.equals(nearby.invoke(null,true,java.util.List.of(block))))throw new AssertionError("Explicit legit mining must retain its policy");
        }
        if(!Boolean.FALSE.equals(nearby.invoke(null,false,java.util.List.of(ore))))throw new AssertionError("Ore policy unchanged");
        System.out.println("SMOKE_OK birch/log/stone/end-stone/sand local exploration and explicit legit/ore separation");
        var recover=goals.getMethod("recoverDepletedSand",boolean.class,boolean.class,boolean.class,boolean.class);
        if(!Boolean.TRUE.equals(recover.invoke(null,true,false,true,true)))throw new AssertionError("Depleted sand may enter discovery");
        if(!Boolean.FALSE.equals(recover.invoke(null,true,true,true,true))
            || !Boolean.FALSE.equals(recover.invoke(null,true,false,true,false))
            || !Boolean.FALSE.equals(recover.invoke(null,false,false,true,true))
            || !Boolean.FALSE.equals(recover.invoke(null,true,false,false,true)))throw new AssertionError("Do not hide real route failures or ignore disabled exploration");
        var targetsOrNull=goals.getMethod("targetsOrNull",java.util.List.class);
        if(targetsOrNull.invoke(null,java.util.List.of())!=null)throw new AssertionError("Empty target list must fall through to discovery");
        Class<?> goal=Class.forName("baritone.api.pathing.goals.Goal",true,target);
        Object known=Class.forName("baritone.api.pathing.goals.GoalXZ",true,target).getConstructor(int.class,int.class).newInstance(0,0);
        Object composite=targetsOrNull.invoke(null,java.util.List.of(known));
        var inGoal=goal.getMethod("isInGoal",int.class,int.class,int.class);
        if(!Boolean.TRUE.equals(inGoal.invoke(composite,0,70,0)))throw new AssertionError("Known targets still win");
        Class<?> blockPos=Class.forName("net.minecraft.core.BlockPos",true,target);
        Object origin=blockPos.getConstructor(int.class,int.class,int.class).newInstance(0,70,0);
        var explore=goals.getMethod("exploration",blockPos,int.class);
        Object surfaceGoal=explore.invoke(null,origin,70),oreGoal=explore.invoke(null,origin,-59);
        // Full horizontal heuristic evaluation initializes BaritoneAPI and requires a real
        // Minecraft client. Check the real goal's stored height and vanilla vertical cost here.
        var preferredY=Class.forName("baritone.api.pathing.goals.GoalRunAway",true,target).getDeclaredField("maintainY");
        preferredY.setAccessible(true);
        if(!Integer.valueOf(70).equals(preferredY.get(surfaceGoal)) || !Integer.valueOf(-59).equals(preferredY.get(oreGoal)))throw new AssertionError("Exploration height not preserved");
        var vertical=Class.forName("baritone.api.pathing.goals.GoalYLevel",true,target).getMethod("calculate",int.class,int.class);
        if((double)vertical.invoke(null,70,70)>=(double)vertical.invoke(null,70,-59))throw new AssertionError("Sand vertical preference");
        if((double)vertical.invoke(null,-59,-59)>=(double)vertical.invoke(null,-59,70))throw new AssertionError("Ore vertical preference");
        if(Boolean.TRUE.equals(inGoal.invoke(surfaceGoal,32,70,0)))throw new AssertionError("Exploration must continue until a real target is found");
        if(!"Mine exploration (location hidden)".equals(surfaceGoal.toString()))throw new AssertionError("Exploration goal location leak");
        System.out.println("SMOKE_OK sand/ore/legit policy, empty-target fallback, exploration height and vertical cost and location-free status");
        Class<?> context=Class.forName("baritone.process.elytra.NetherPathfinderContext", true, target);
        if (!supported.equals(context.getMethod("isSupported").invoke(null))) throw new AssertionError("Native capability mismatch");
        checkTrailSupplyItems(target);
        checkFilledChunkPacking(target);
        System.out.println("SMOKE_OK Baritone startup pathfinder dependency and capability probe; supported="+supported);
        Class<?> gate = Class.forName("dev.autostore.DependencyGate", true, target);
        var check = gate.getDeclaredMethod("check");
        check.setAccessible(true);
        check.invoke(null);
        System.out.println("SMOKE_OK runtime dependency checksums");
        System.out.println("SMOKE_COMPLETE: no Minecraft client, world, account, or network session started.");
    }
    private static void checkMiningResume(ClassLoader target,Object unsafe,java.lang.reflect.Method allocate)throws Exception {
        Class<?> minerClass=Class.forName("baritone.process.MineProcess",true,target);
        Object miner=allocate.invoke(unsafe,minerClass);
        Object filter=allocate.invoke(unsafe,Class.forName("baritone.api.utils.BlockOptionalMetaLookup",true,target));
        Object cooldown=Class.forName("baritone.cache.TargetCooldown",true,target).getConstructor().newInstance();
        Object position=Class.forName("net.minecraft.core.BlockPos",true,target).getConstructor(int.class,int.class,int.class).newInstance(1,70,1);
        cooldown.getClass().getMethod("reject",Object.class,long.class).invoke(cooldown,position,100L);
        fixtureField(miner,"filter",filter);fixtureField(miner,"desiredQuantity",321);
        fixtureField(miner,"searchGeneration",7L);fixtureField(miner,"failedTargets",cooldown);
        fixtureField(miner,"blacklist",java.util.List.of(position));fixtureField(miner,"knownOreLocations",java.util.List.of(position));
        fixtureField(miner,"branchPoint",position);fixtureField(miner,"exploringSand",true);fixtureField(miner,"tickCount",17);
        minerClass.getMethod("resumeAfterStorage").invoke(miner);
        for(String field:new String[]{"blacklist","knownOreLocations"})if(!((java.util.List<?>)fixtureValue(miner,field)).isEmpty())throw new AssertionError(field+" retained stale targets");
        if(!((java.util.List<?>)cooldown.getClass().getMethod("snapshot",long.class).invoke(cooldown,101L)).isEmpty())throw new AssertionError("Cooldown not cleared");
        if(fixtureValue(miner,"filter")!=filter || !Integer.valueOf(321).equals(fixtureValue(miner,"desiredQuantity")))throw new AssertionError("Native target or quantity replaced");
        if(!Long.valueOf(8L).equals(fixtureValue(miner,"searchGeneration")) || !Boolean.TRUE.equals(fixtureValue(miner,"searchRefreshPending")))throw new AssertionError("Old scans not invalidated / refresh not pending");
        if(fixtureValue(miner,"branchPoint")!=null || !Boolean.FALSE.equals(fixtureValue(miner,"exploringSand")) || !Integer.valueOf(0).equals(fixtureValue(miner,"tickCount")))throw new AssertionError("Stale exploration state");
        fixtureField(miner,"filter",null);minerClass.getMethod("resumeAfterStorage").invoke(miner);
        if(!Long.valueOf(8L).equals(fixtureValue(miner,"searchGeneration")))throw new AssertionError("Inactive job changed");
        System.out.println("SMOKE_OK native mining resume clears rejected targets, invalidates old scans and preserves target/quantity; inactive job unchanged");
    }
    private static Object fixtureValue(Object fixture,String name)throws Exception {
        var field=fixture.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(fixture);
    }
    private static Object supplyStack(ClassLoader target,String item,int count,java.util.Map<String,Object> extra)throws Exception {
        Class<?> map=Class.forName("net.minecraft.core.component.DataComponentMap",true,target),builderClass=Class.forName("net.minecraft.core.component.DataComponentMap$Builder",true,target);
        Class<?> component=Class.forName("net.minecraft.core.component.DataComponentType",true,target),components=Class.forName("net.minecraft.core.component.DataComponents",true,target);
        Object builder=map.getMethod("builder").invoke(null);
        var set=builderClass.getMethod("set",component,Object.class);
        set.invoke(builder,components.getField("MAX_STACK_SIZE").get(null),item.contains("SHULKER")?1:64);
        for(var entry:extra.entrySet())set.invoke(builder,components.getField(entry.getKey()).get(null),entry.getValue());
        Object base=builderClass.getMethod("build").invoke(builder);
        Class<?> patched=Class.forName("net.minecraft.core.component.PatchedDataComponentMap",true,target),holder=Class.forName("net.minecraft.core.Holder",true,target);
        Class<?> itemClass=Class.forName("net.minecraft.world.item.Item",true,target),stack=Class.forName("net.minecraft.world.item.ItemStack",true,target);
        Object itemValue=Class.forName("net.minecraft.world.item.Items",true,target).getField(item).get(null);
        Object itemHolder=itemClass.getMethod("builtInRegistryHolder").invoke(itemValue);
        // Bind synthetic defaults only in this isolated probe; ordinary clients load these from datapacks.
        if(!Boolean.TRUE.equals(itemHolder.getClass().getMethod("areComponentsBound").invoke(itemHolder)))itemHolder.getClass().getMethod("bindComponents",map).invoke(itemHolder,base);
        var constructor=stack.getDeclaredConstructor(holder,int.class,patched);constructor.setAccessible(true);
        return constructor.newInstance(itemHolder,count,patched.getConstructor(map).newInstance(base));
    }
    private static void checkTrailSupplyItems(ClassLoader target)throws Exception {
        Class<?> fireworks=Class.forName("net.minecraft.world.item.component.Fireworks",true,target);
        Object flight=fireworks.getConstructor(int.class,java.util.List.class).newInstance(1,java.util.List.of());
        Object rockets=supplyStack(target,"FIREWORK_ROCKET",64,java.util.Map.of("FIREWORKS",flight));
        Object dirt=supplyStack(target,"DIRT",1,java.util.Map.of());
        Class<?> container=Class.forName("net.minecraft.world.item.component.ItemContainerContents",true,target);
        Object fuel=container.getMethod("fromItems",java.util.List.class).invoke(null,java.util.List.of(rockets));
        Object mixed=container.getMethod("fromItems",java.util.List.class).invoke(null,java.util.List.of(rockets,dirt));
        Object box=supplyStack(target,"SHULKER_BOX",1,java.util.Map.of("CONTAINER",fuel));
        Object unsafeBox=supplyStack(target,"SHULKER_BOX",1,java.util.Map.of("CONTAINER",mixed));
        Object empty=supplyStack(target,"SHULKER_BOX",1,java.util.Map.of());
        Class<?> ctrl=Class.forName("dev.autostore.trail.TrailController",true,target),stack=Class.forName("net.minecraft.world.item.ItemStack",true,target);
        var safe=ctrl.getDeclaredMethod("supplyBox",stack);safe.setAccessible(true);
        var count=ctrl.getDeclaredMethod("boxRocketCount",stack);count.setAccessible(true);
        if(!Boolean.TRUE.equals(safe.invoke(null,box)) || !Integer.valueOf(64).equals(count.invoke(null,box)))throw new AssertionError("Supply shulker must expose actual template contents");
        if(!Boolean.FALSE.equals(safe.invoke(null,unsafeBox)))throw new AssertionError("Mixed valuable-item shulker must not be used as fuel");
        if(!Boolean.TRUE.equals(safe.invoke(null,empty)) || !Integer.valueOf(0).equals(count.invoke(null,empty)))throw new AssertionError("Empty recovered box remains recognizable");
        if(Boolean.getBoolean("autostore.smoke.xaero")) {
            Class<?> detector=Class.forName("pwn.noobs.trouserstreak.modules.NewerNewChunks",false,target);
            for(String field:new String[]{"newChunks","oldChunks","OldGenerationOldChunks","beingUpdatedOldChunks"})
                if(!java.util.Set.class.isAssignableFrom(detector.getDeclaredField(field).getType()))throw new AssertionError("Trouser chunk adapter field changed");
        }
        System.out.println("SMOKE_OK real 26.3 shulker ItemStackTemplate contents, fuel count, mixed-item rejection and empty-box recovery");
    }
    private static void checkFilledChunkPacking(ClassLoader target) throws Exception {
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Class<?> block=Class.forName("net.minecraft.world.level.block.Block",true,target);
        Class<?> blocks=Class.forName("net.minecraft.world.level.block.Blocks",true,target);
        Class<?> strategy=Class.forName("net.minecraft.world.level.chunk.Strategy",true,target);
        Object blockStrategy=strategy.getMethod("createForBlockStates",Class.forName("net.minecraft.core.IdMap",true,target))
            .invoke(null,block.getField("BLOCK_STATE_REGISTRY").get(null));
        Class<?> palette=Class.forName("net.minecraft.world.level.chunk.PalettedContainer",true,target);
        Class<?> section=Class.forName("net.minecraft.world.level.chunk.LevelChunkSection",true,target);
        Class<?> chunk=Class.forName("net.minecraft.world.level.chunk.LevelChunk",true,target);
        var sections=Class.forName("net.minecraft.world.level.chunk.ChunkAccess",true,target).getDeclaredField("sections");sections.setAccessible(true);
        var write=Class.forName("baritone.process.elytra.NetherPathfinderContext",true,target).getDeclaredMethod("writeChunkData",chunk,long.class);write.setAccessible(true);
        // A 16-cubed section occupies 4096 bits, or 512 bytes. Guard the complete
        // 24-section buffer and inspect every byte, including unaffected sections.
        final int sectionBytes=512,total=24*sectionBytes;
        long allocation=unsafe.allocateMemory(total+2);
        int cases=0;
        try {
            for(String material:new String[]{"LAVA","NETHERRACK","AIR"}) {
                Object state=block.getMethod("defaultBlockState").invoke(blocks.getField(material).get(null));
                for(int index:new int[]{0,1,7,23}) {
                    Object states=palette.getConstructor(Object.class,strategy).newInstance(state,blockStrategy);
                    Object filled=section.getConstructor(palette,Class.forName("net.minecraft.world.level.chunk.PalettedContainerRO",true,target)).newInstance(states,null);
                    Object array=java.lang.reflect.Array.newInstance(section,24);java.lang.reflect.Array.set(array,index,filled);
                    Object fixture=unsafe.allocateInstance(chunk);sections.set(fixture,array);
                    unsafe.setMemory(allocation,total+2,(byte)0);
                    unsafe.putByte(allocation,(byte)0x5A);unsafe.putByte(allocation+total+1,(byte)0x6B);
                    write.invoke(null,fixture,allocation+1);
                    for(int offset=0;offset<total;offset++) {
                        byte expected=(byte)(!material.equals("AIR") && offset/sectionBytes==index ? 0xFF : 0);
                        if(unsafe.getByte(allocation+1+offset)!=expected)
                            throw new AssertionError("Incorrect filled-section occupancy: "+material+" section="+index+" byte="+offset);
                    }
                    if(unsafe.getByte(allocation)!=(byte)0x5A || unsafe.getByte(allocation+total+1)!=(byte)0x6B)
                        throw new AssertionError("Chunk packing wrote outside its buffer");
                    cases++;
                }
            }
        } finally {unsafe.freeMemory(allocation);}
        System.out.println("SMOKE_OK actual chunk packing: "+cases+" lava/solid/air cases, section offsets, adjacent sections and buffer guards");
    }
    private static void checkHeadlessMenu(ClassLoader target,Object unsafe,java.lang.reflect.Method allocate,Object mc,Object gui,Object window,Object controller,Object player,Object menu)throws Exception {
        fixtureField(gui,"minecraft",mc);fixtureField(mc,"player",player);fixtureField(player,"containerMenu",menu);
        fixtureField(controller,"ownsScreen",true);
        Class<?> screen=Class.forName("net.minecraft.client.gui.screens.Screen",true,target);
        Object chestScreen=allocate.invoke(unsafe,Class.forName("net.minecraft.client.gui.screens.inventory.ContainerScreen",true,target));
        fixtureField(chestScreen,"menu",menu);
        var setScreen=gui.getClass().getMethod("setScreen",screen);
        setScreen.invoke(gui,chestScreen);setScreen.invoke(gui,new Object[]{null});
        var shown=gui.getClass().getMethod("screen");
        if(shown.invoke(gui)!=null)throw new AssertionError("Background chest must retain server menu without presenting GUI");
        fixtureField(window,"focused",true);
        expectGuiPath(setScreen,gui,chestScreen);
        fixtureField(window,"focused",false);fixtureField(controller,"ownsScreen",false);
        expectGuiPath(setScreen,gui,chestScreen);
        fixtureField(gui,"screen",null);fixtureField(controller,"ownsScreen",true);
        Class<?> mouseClass=Class.forName("net.minecraft.client.MouseHandler",true,target);
        Object mouse=allocate.invoke(unsafe,mouseClass);fixtureField(mouse,"minecraft",mc);
        mouseClass.getMethod("resyncMousePosition").invoke(mouse); // must not touch SDL at all
        var ignore=mouseClass.getDeclaredField("ignoreFirstMove");ignore.setAccessible(true);
        if(!ignore.getBoolean(mouse))throw new AssertionError("Unfocused menu transition must defer native cursor query");
        fixtureField(mouse,"ignoreFirstMove",false);fixtureField(mouse,"mouseGrabbed",true);fixtureField(window,"focused",true);
        mouseClass.getMethod("resyncMousePosition").invoke(mouse);
        if(ignore.getBoolean(mouse))throw new AssertionError("Focused cursor behavior must stay native");
        fixtureField(window,"focused",false);fixtureField(controller,"ownsScreen",false);fixtureField(mc,"player",null);
        System.out.println("SMOKE_OK actual headless owned-container open/close; focused/unowned GUI fallback; background native mouse query bypass");
    }
    private static void expectGuiPath(java.lang.reflect.Method setter,Object gui,Object screen)throws Exception {
        try{setter.invoke(gui,screen);throw new AssertionError("Normal GUI transition must not be cancelled");}
        catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof NullPointerException))throw expected;}
    }
    private static void checkTrailAdapter(ClassLoader target)throws Exception {
        Class<?> route=Class.forName("dev.autostore.trail.TrailRoute",true,target);
        Class<?> cell=Class.forName("dev.autostore.trail.TrailRoute$Cell",true,target);
        Class<?> chunk=Class.forName("net.minecraft.world.level.ChunkPos",true,target);
        Object planner=route.getConstructor(double.class,double.class).newInstance(1.0,0.0),center=cell.getConstructor(int.class,int.class).newInstance(0,0);
        java.util.Set<Object> old=new java.util.HashSet<>(),fresh=new java.util.HashSet<>();
        for(int i=0;i<=10;i++)old.add(chunk.getConstructor(int.class,int.class).newInstance(i,0));
        fresh.add(chunk.getConstructor(int.class,int.class).newInstance(3,0));
        Class.forName("dev.autostore.trail.TrouserTrail",true,target).getMethod("sampleSets",route,cell,java.util.Set.class,java.util.Set.class,java.util.Set.class,java.util.Set.class)
            .invoke(null,planner,center,fresh,old,java.util.Set.of(),java.util.Set.of());
        var next=(java.util.Optional<?>)route.getMethod("next",cell,int.class).invoke(planner,center,14);
        if(next.isPresent())throw new AssertionError("Real ChunkPos adapter must prioritize new terrain over an old-trail classification");
        System.out.println("SMOKE_OK real ChunkPos-to-trail adapter and fresh-terrain boundary");
    }
    private static void fixtureField(Object fixture,String name,Object value)throws Exception {
        for(Class<?> type=fixture.getClass();type!=null;type=type.getSuperclass()) {
            try {var field=type.getDeclaredField(name);field.setAccessible(true);field.set(fixture,value);return;}
            catch(NoSuchFieldException ignored){}
        }
        throw new NoSuchFieldException(name);
    }
    private static void expectNativeRender(java.lang.reflect.Method frame,Object fixture)throws Exception {
        try {frame.invoke(fixture,true);throw new AssertionError("Normal rendering must not be cancelled");}
        catch(java.lang.reflect.InvocationTargetException expected) {
            if(!(expected.getCause() instanceof NullPointerException))throw expected;
            // The untouched vanilla entry dereferences the deliberately absent GPU surface.
            if(!expected.getCause().getMessage().contains("windowSurface"))throw expected;
        }
    }

}
