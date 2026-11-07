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
            frame.invoke(mcFixture,true);frame.invoke(mcFixture,true);
            fixtureField(windowFixture,"focused",true);
            expectNativeRender(frame,mcFixture); // focused bot must keep normal rendering
            fixtureField(windowFixture,"focused",false);fixtureField(controllerFixture,"manuallyPaused",true);
            expectNativeRender(frame,mcFixture); // explicit pause restores ordinary client behavior
            fixtureField(controllerFixture,"manuallyPaused",false);fixtureField(controllerFixture,"armed",false);
            expectNativeRender(frame,mcFixture); // no bot means no global rendering change
        } finally {instance.set(null,oldInstance);}
        System.out.println("SMOKE_OK actual transformed background frame bypass; foreground, pause and disarmed fall through normally");
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
        checkFilledChunkPacking(target);
        System.out.println("SMOKE_OK Baritone startup pathfinder dependency and capability probe; supported="+supported);
        Class<?> gate = Class.forName("dev.autostore.DependencyGate", true, target);
        var check = gate.getDeclaredMethod("check");
        check.setAccessible(true);
        check.invoke(null);
        System.out.println("SMOKE_OK runtime dependency checksums");
        System.out.println("SMOKE_COMPLETE: no Minecraft client, world, account, or network session started.");
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
    private static void fixtureField(Object fixture,String name,Object value)throws Exception {
        var field=fixture.getClass().getDeclaredField(name);field.setAccessible(true);field.set(fixture,value);
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
