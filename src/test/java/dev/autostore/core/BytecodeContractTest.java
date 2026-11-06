package dev.autostore.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Checks actual pinned binaries without starting a game or accessing a server. Not a runtime load test. */
class BytecodeContractTest {
    @Test void allRequiredMixinSelectorsAndCallbackSignaturesMatchPinnedJars() throws Exception {
        int checked=0;
        for(String mixin:List.of("BreakGuardMixin","ContainerSnapshotMixin","AutoEatMixin","AutoToolMixin","AutoToolAccess","KillAuraMixin","InteractionGuardMixin","BackgroundMiningMixin","BackgroundFpsMixin","PauseMenuMixin","BackgroundFrameMixin")) {
            ClassNode source=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/mixin/"+mixin+".class")));
            AnnotationNode annotation=annotations(source.visibleAnnotations,source.invisibleAnnotations).stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") List<Type> targets=(List<Type>)value(annotation,"value");
            assertEquals(1,targets.size());String targetName=targets.getFirst().getInternalName();
            String jar=targetName.startsWith("baritone/")?"baritone.jar":targetName.startsWith("meteordevelopment/")?"meteor.jar":"minecraft-26.2.jar";
            ClassNode target;
            try(JarFile file=new JarFile("deps/"+jar)){target=read(file.getInputStream(file.getJarEntry(targetName+".class")).readAllBytes());}
            for(MethodNode method:source.methods)for(AnnotationNode injection:annotations(method.visibleAnnotations,method.invisibleAnnotations)) {
                if(!injection.desc.endsWith("/Inject;"))continue;
                @SuppressWarnings("unchecked") List<String> selectors=(List<String>)value(injection,"method");
                for(String selector:selectors) {
                    List<MethodNode> matches=target.methods.stream().filter(m -> selector.contains("(")?(m.name+m.desc).equals(selector):m.name.equals(selector)).toList();
                    assertEquals(1,matches.size(),mixin+" selector "+selector);
                    MethodNode original=matches.getFirst();
                    assertEquals(original.access&Opcodes.ACC_STATIC,method.access&Opcodes.ACC_STATIC);
                    Type[] callbackArgs=Type.getArgumentTypes(method.desc),targetArgs=Type.getArgumentTypes(original.desc);
                    if(callbackArgs.length>1) assertArrayEquals(targetArgs,Arrays.copyOf(callbackArgs,callbackArgs.length-1));
                    boolean returnsVoid=Type.getReturnType(original.desc).getSort()==Type.VOID;
                    assertEquals(returnsVoid?"org/spongepowered/asm/mixin/injection/callback/CallbackInfo":"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",callbackArgs[callbackArgs.length-1].getInternalName());
                    checked++;
                }
            }
        }
        assertEquals(14,checked);
    }
    @Test void autoToolInvokerAndKillAuraRedirectMatchThePinnedMeteorBytecode() throws Exception {
        try(JarFile jar=new JarFile("deps/meteor.jar")) {
            ClassNode tool=read(jar.getInputStream(jar.getJarEntry("meteordevelopment/meteorclient/systems/modules/player/AutoTool.class")).readAllBytes());
            assertEquals(1,tool.methods.stream().filter(m -> m.name.equals("shouldStopUsing") && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z")).count());
            ClassNode aura=read(jar.getInputStream(jar.getJarEntry("meteordevelopment/meteorclient/systems/modules/combat/KillAura.class")).readAllBytes());
            MethodNode tick=aura.methods.stream().filter(m -> m.name.equals("onTick")).findFirst().orElseThrow();
            int matches=0;
            for(AbstractInsnNode instruction:tick.instructions) if(instruction instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/client/multiplayer/MultiPlayerGameMode") && call.name.equals("isDestroying") && call.desc.equals("()Z"))matches++;
            assertEquals(1,matches);
        }
    }
    @Test void storageNeverIssuesMiningCommandsAndRoutinePathCancelPreservesProcesses() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        for(MethodNode method:controller.methods) for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call && (call.name.equals("mine") || call.name.equals("mineByName")))
                fail("AutoStore must not issue mining commands: "+method.name);
        MethodNode cancel=controller.methods.stream().filter(m -> m.name.equals("cancel")).findFirst().orElseThrow();
        boolean segmentOnly=false;
        for(AbstractInsnNode instruction:cancel.instructions) if(instruction instanceof MethodInsnNode call) {
            assertNotEquals("cancelEverything",call.name,"Deposit trips must retain the native miner");
            if(call.name.equals("cancelSegmentIfSafe"))segmentOnly=true;
        }
        assertTrue(segmentOnly);
        ClassNode miner=read(Files.readAllBytes(Path.of("build/classes/java/main/baritone/process/MineProcess.class")));
        for(MethodNode method:miner.methods) if(!method.name.equals("mine"))
            for(AbstractInsnNode instruction:method.instructions) if(instruction instanceof MethodInsnNode call)
                assertNotEquals("dev/autostore/Controller",call.owner,"Do not filter or retarget native mining algorithms");
    }
    @Test void passiveMenuPauseRedirectMatchesVanillaGui() throws Exception {
        try(JarFile jar=new JarFile("deps/minecraft-26.2.jar")) {
            ClassNode gui=read(jar.getInputStream(jar.getJarEntry("net/minecraft/client/gui/Gui.class")).readAllBytes());
            MethodNode pausing=gui.methods.stream().filter(m -> m.name.equals("isPausing") && m.desc.equals("()Z")).findFirst().orElseThrow();
            int matches=0;
            for(AbstractInsnNode instruction:pausing.instructions)if(instruction instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/client/gui/screens/Screen") && call.name.equals("isPauseScreen") && call.desc.equals("()Z"))matches++;
            assertEquals(1,matches,"Pause redirect must affect exactly the real screen pause decision");
        }
    }
    @Test void finalBreakGuardProtectsMiningBeforeAnyStorageOnlyEarlyReturn() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode guard=controller.methods.stream().filter(m -> m.name.equals("permitBreak")).findFirst().orElseThrow();
        int position=0,toolCheck=-1,storageCheck=-1;
        for(AbstractInsnNode instruction:guard.instructions) {
            if(instruction instanceof MethodInsnNode call) {
                if(call.name.equals("prepareMiningTool"))toolCheck=position;
                if(call.name.equals("cooperativeJob"))storageCheck=position;
            }
            position++;
        }
        assertTrue(toolCheck>=0 && toolCheck<storageCheck,"Unconfigured/quantity-limited mining must not bypass the tool reserve");
        ClassNode tools=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningTools.class")));
        for(MethodNode method:tools.methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call) {
                assertNotEquals("mine",call.name,"Refilling tools must retain the existing mining process");
                assertNotEquals("mineByName",call.name);
                assertNotEquals("drop",call.name,"Retired tools must not be thrown away");
            }
    }
    @Test void everyBuilderReplacementEndsOldSupervisionBeforeChangingSchematic() throws Exception {
        ClassNode builder=read(Files.readAllBytes(Path.of("build/classes/java/main/baritone/process/BuilderProcess.class")));
        MethodNode build=builder.methods.stream().filter(m -> m.name.equals("build") && m.desc.contains("ISchematic;")).findFirst().orElseThrow();
        int offset=0,hook=-1,write=-1;
        for(AbstractInsnNode instruction:build.instructions) {
            if(instruction instanceof MethodInsnNode call && call.name.equals("beforeNativeBuild"))hook=offset;
            if(instruction instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD && field.name.equals("schematic") && write<0)write=offset;
            offset++;
        }
        assertTrue(hook>=0 && hook<write,"Old job must be stopped before assigning the new builder schematic");
    }
    @Test void vanillaSupportsFullResyncAfterStaleStateAndNormalMineUsesEligibleScan() throws Exception {
        try(JarFile jar=new JarFile("deps/minecraft-26.2.jar")) {
            ClassNode server=read(jar.getInputStream(jar.getJarEntry("net/minecraft/server/network/ServerGamePacketListenerImpl.class")).readAllBytes());
            MethodNode click=server.methods.stream().filter(m -> m.name.equals("handleContainerClick")).findFirst().orElseThrow();
            Set<String> calls=new HashSet<>();
            for(AbstractInsnNode instruction:click.instructions)if(instruction instanceof MethodInsnNode call)calls.add(call.name);
            assertTrue(calls.containsAll(Set.of("isValidSlotIndex","stateId","getStateId","clicked","broadcastFullState")));
        }
        ClassNode miner=read(Files.readAllBytes(Path.of("build/classes/java/main/baritone/process/MineProcess.class")));
        MethodNode search=miner.methods.stream().filter(m -> m.name.equals("searchWorld")).findFirst().orElseThrow();
        boolean eligible=false;
        for(AbstractInsnNode instruction:search.instructions)if(instruction instanceof MethodInsnNode call) {
            assertNotEquals("scanChunkRadius",call.name,"Rejected hits must never exhaust the mining scan quota");
            if(call.name.equals("scanEligibleChunkRadius"))eligible=true;
        }
        assertTrue(eligible);
    }
    @Test void unfinishedBreaksReachRecoveryAndIdleTimerDoesNotDependOnGroundOrSafePath() throws Exception {
        ClassNode mixin=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/mixin/BreakGuardMixin.class")));
        MethodNode before=mixin.methods.stream().filter(m->m.name.equals("beforeBreak")).findFirst().orElseThrow();
        assertTrue(calls(before).contains("observeBreakAttempt"));
        ClassNode recovery=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningRecovery.class")));
        MethodNode update=recovery.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
        assertTrue(calls(update).contains("canMonitorMine"));assertFalse(calls(update).contains("canRecoverMine"));
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode monitor=controller.methods.stream().filter(m->m.name.equals("canMonitorMine")).findFirst().orElseThrow();
        assertFalse(calls(monitor).contains("onGround"));assertFalse(calls(monitor).contains("isSafeToCancel"));
    }
    @Test void deliberateRelogResumesServerStateWithoutOldInventoryOrPositionComparison() throws Exception {
        ClassNode recovery=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningRecovery.class")));
        MethodNode update=recovery.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
        Set<String> names=calls(update);
        assertTrue(names.containsAll(Set.of("mine","recoveryScope","isAlive","hasChunkAt")));
        assertFalse(names.contains("sameInventory"));assertFalse(names.contains("distanceToSqr"));
        for(MethodNode m:recovery.methods)for(AbstractInsnNode i:m.instructions)
            if(i instanceof MethodInsnNode c)assertFalse(c.owner.equals("net/minecraft/world/item/ItemStack") && c.name.equals("matches"));
    }
    @Test void storageWaitsForFullReplyAndDoesNotThrowOnChangedOpeningContents() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode ready=controller.methods.stream().filter(m->m.name.equals("ready")).findFirst().orElseThrow();
        assertFalse(calls(ready).contains("require"));assertTrue(calls(ready).contains("requestStorageRefresh"));
        for(String method:List.of("click","rebaseTransfer")) {
            MethodNode m=controller.methods.stream().filter(x->x.name.equals(method)).findFirst().orElseThrow();
            boolean full=false;
            for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode f && f.name.equals("fullSnapshotRevision") && f.getOpcode()==Opcodes.GETFIELD)full=true;
            assertTrue(full,"Fresh full server response required: "+method);
        }
    }
    @Test void damageRecoveryBypassesCombatGateButKeepsTransactionAndPauseGuards() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode gate=controller.methods.stream().filter(m->m.name.equals("recoveryBlocker") && m.desc.startsWith("(L")).findFirst().orElseThrow();
        assertFalse(calls(gate).contains("busy"));
        assertTrue(calls(gate).containsAll(Set.of("waiting","isAlive","miningActive")));
        Set<String> fields=new HashSet<>();
        for(AbstractInsnNode i:gate.instructions)if(i instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETFIELD)fields.add(f.name);
        assertTrue(fields.containsAll(Set.of("manuallyPaused","before","ownsScreen")));
        MethodNode damage=controller.methods.stream().filter(m->m.name.equals("mobDamage")).findFirst().orElseThrow();
        int observed=-1,entityLookup=-1,index=0;
        for(AbstractInsnNode i:damage.instructions) {
            if(i instanceof MethodInsnNode c){if(c.name.equals("damaged"))observed=index;if(c.name.equals("getEntity"))entityLookup=index;}
            index++;
        }
        assertTrue(observed>=0 && observed<entityLookup,"Damage detection must not depend on a locally visible attacker");
        ClassNode recovery=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningRecovery.class")));
        MethodNode update=recovery.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
        boolean urgent=false;
        for(AbstractInsnNode i:update.instructions)if(i instanceof MethodInsnNode c && c.name.equals("beginRelog") && c.desc.endsWith("Z)V")) {
            AbstractInsnNode previous=i.getPrevious();while(previous!=null && previous.getOpcode()<0)previous=previous.getPrevious();
            urgent=previous!=null && previous.getOpcode()==Opcodes.ICONST_1;
        }
        assertTrue(urgent,"Sustained damage must request the urgent cooldown bypass");
    }
    @Test void backgroundFrameSkipsGraphicsButPreservesPickingGuiUpdatesAndTaskDrain() throws Exception {
        ClassNode mixin=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/mixin/BackgroundFrameMixin.class")));
        MethodNode hook=mixin.methods.stream().filter(m->m.name.equals("keepBotRunningWithoutDrawing")).findFirst().orElseThrow();
        Set<String> names=calls(hook);
        assertTrue(names.containsAll(Set.of("keepRunningInMenus","isWindowActive","advanceRealTime","update","pick","executePendingTasks","limitDisplayFPS","cancel")));
        assertFalse(names.contains("tick"),"Must not duplicate ticks or invoke game code on a separate thread");
        for(String forbidden:List.of("render","extract","present","acquireNextTexture","grabMouse","glfwFocusWindow","setWindowActive"))
            assertFalse(names.contains(forbidden));
        AnnotationNode injection=annotations(hook.visibleAnnotations,hook.invisibleAnnotations).stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE,value(injection,"cancellable"));
        @SuppressWarnings("unchecked") List<AnnotationNode> at=(List<AnnotationNode>)value(injection,"at");
        assertEquals("HEAD",value(at.getFirst(),"value"));
    }
    @Test void backgroundRenderingCancellationCannotBypassVanillaPacketsTicksOrEventPolling() throws Exception {
        try(JarFile jar=new JarFile("deps/minecraft-26.2.jar")) {
            ClassNode mc=read(jar.getInputStream(jar.getJarEntry("net/minecraft/client/Minecraft.class")).readAllBytes());
            MethodNode run=mc.methods.stream().filter(m->m.name.equals("run")).findFirst().orElseThrow();
            assertTrue(calls(run).contains("pollEvents"));
            MethodNode tick=mc.methods.stream().filter(m->m.name.equals("runTick")).findFirst().orElseThrow();
            Set<String> beforeRender=new HashSet<>();
            for(AbstractInsnNode i:tick.instructions)if(i instanceof MethodInsnNode c){if(c.name.equals("renderFrame"))break;beforeRender.add(c.name);}
            assertTrue(beforeRender.containsAll(Set.of("advanceGameTime","processQueuedPackets","runAllTasks","tick")));
            MethodNode render=mc.methods.stream().filter(m->m.name.equals("renderFrame")).findFirst().orElseThrow();
            assertTrue(calls(render).containsAll(Set.of("acquireNextTexture","present")),"Verify the GPU surface waits being bypassed");
        }
    }
    @Test void recoveryCountsObstructingLeavesAsWellAsRequestedLogs() throws Exception {
        ClassNode recovery=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningRecovery.class")));
        MethodNode attempt=recovery.methods.stream().filter(m->m.name.equals("attempted")).findFirst().orElseThrow();
        assertTrue(calls(attempt).containsAll(Set.of("canMonitorMine","isAir")));
        assertFalse(calls(attempt).contains("recoveryTarget"),"Access-block failures must not be excluded by the mining target");
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode eligibility=controller.methods.stream().filter(m->m.name.equals("canMonitorDamageRecovery")).findFirst().orElseThrow();
        assertTrue(calls(eligibility).contains("recoveryBlocker"),"Status and actual eligibility must use the same blockers");
    }
    @Test void startupScreenCannotDisarmSupervisionAndOnlyCapturedEmptyCursorMenuCanClose() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode validate=controller.methods.stream().filter(m->m.name.equals("validate")).findFirst().orElseThrow();
        assertFalse(calls(validate).contains("externalScreen"),"Screen waits must be recoverable rather than a startup exception");
        MethodNode startup=controller.methods.stream().filter(m->m.name.equals("startObservedJob")).findFirst().orElseThrow();
        Set<String> menuTypes=new HashSet<>();
        for(AbstractInsnNode i:startup.instructions)if(i instanceof TypeInsnNode type && type.getOpcode()==Opcodes.INSTANCEOF)menuTypes.add(type.desc);
        assertTrue(menuTypes.containsAll(Set.of("net/minecraft/world/inventory/ChestMenu","net/minecraft/world/inventory/ShulkerBoxMenu")));
        MethodNode finish=controller.methods.stream().filter(m->m.name.equals("finishStartupContainer")).findFirst().orElseThrow();
        assertTrue(calls(finish).containsAll(Set.of("cooperativeJob","miningActive","getCarried","closeContainer")));
        assertFalse(calls(finish).contains("handleContainerInput"));assertFalse(calls(finish).contains("isWindowActive"));
        Set<String> fields=new HashSet<>();boolean identity=false;
        for(AbstractInsnNode i:finish.instructions) {
            if(i instanceof FieldInsnNode field)fields.add(field.name);
            if(i.getOpcode()==Opcodes.IF_ACMPEQ || i.getOpcode()==Opcodes.IF_ACMPNE)identity=true;
        }
        assertTrue(identity);assertTrue(fields.containsAll(Set.of("startupContainer","containerMenu","manuallyPaused","before","ownsScreen")));
    }
    private static Set<String> calls(MethodNode method) {
        Set<String> calls=new HashSet<>();
        for(AbstractInsnNode i:method.instructions)if(i instanceof MethodInsnNode c)calls.add(c.name);
        return calls;
    }
    @Test void exactRuntimeDependenciesArePinned() throws Exception {
        assertEquals("c84e494dc066133bb1a45bba34d3148f26474f3272bb5865475d63222fc352ae",hash("deps/meteor.jar"));
    }
    private String hash(String path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path))));}
    private static ClassNode read(byte[] bytes){ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return n;}
    private static List<AnnotationNode> annotations(List<AnnotationNode>a,List<AnnotationNode>b){List<AnnotationNode> all=new ArrayList<>();if(a!=null)all.addAll(a);if(b!=null)all.addAll(b);return all;}
    private static Object value(AnnotationNode annotation,String key){for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);throw new AssertionError(key);}
    @Test void storageArrivalAndOpenRequireActualReachAndLayoutRefreshGuardsPendingTransfers() throws Exception {
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        for(String method:List.of("arrived","open")) {
            MethodNode node=controller.methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();
            boolean reach=false;
            for(AbstractInsnNode instruction:node.instructions) {
                if(instruction instanceof FieldInsnNode field)assertNotEquals("stand",field.name,"Old standing spot alone cannot count as arrival");
                if(instruction instanceof MethodInsnNode call && call.name.equals("reachableHit"))reach=true;
            }
            assertTrue(reach,method);
        }
        MethodNode layout=controller.methods.stream().filter(m->m.name.equals("depotValid")).findFirst().orElseThrow();
        int guard=-1,layoutWrite=-1;
        for(int i=0;i<layout.instructions.size();i++)if(layout.instructions.get(i) instanceof FieldInsnNode field) {
            if(field.name.equals("before") && field.getOpcode()==Opcodes.GETFIELD)guard=i;
            if(field.name.equals("size") && field.getOpcode()==Opcodes.PUTFIELD)layoutWrite=i;
        }
        assertTrue(guard>=0 && layoutWrite>guard,"Refresh layout only after checking for a pending transfer");
        MethodNode hit=controller.methods.stream().filter(m->m.name.equals("reachableHit")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(hit.instructions.toArray()).anyMatch(n->n instanceof FieldInsnNode f && f.name.equals("otherHalf")),"Both halves must participate in reach testing");
    }

    @Test void exhaustedStorageRequestsRateLimitedRelogAndRetriesStorageAfterRestoringTarget() throws Exception {
        ClassNode recovery=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/MiningRecovery.class")));
        MethodNode update=recovery.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
        int mine=-1,storage=-1,gate=-1,request=-1,index=0;
        for(AbstractInsnNode insn:update.instructions) {
            if(insn instanceof MethodInsnNode call) {
                if(call.name.equals("mine"))mine=index;
                if(call.name.equals("retryStorageAfterRecovery"))storage=index;
                if(call.name.equals("canRecoverStorage"))gate=index;
                if(call.name.equals("beginRelog") && call.desc.equals("(JLjava/lang/String;ZZ)V")) {
                    request=index;
                    AbstractInsnNode last=insn.getPrevious();while(last.getOpcode()<0)last=last.getPrevious();
                    assertEquals(Opcodes.ICONST_1,last.getOpcode(),"Storage retry ticket must be set");
                    last=last.getPrevious();while(last.getOpcode()<0)last=last.getPrevious();
                    assertEquals(Opcodes.ICONST_0,last.getOpcode(),"Storage must not bypass the reconnect cooldown");
                }
            }
            index++;
        }
        assertTrue(mine>=0 && storage>mine,"Re-arm the saved target, then take storage control in the same tick");
        assertTrue(gate>=0 && request>gate,"Exhausted storage must reach the reconnect coordinator");
        MethodNode begin=recovery.methods.stream().filter(m->m.name.equals("beginRelog") && m.desc.equals("(JLjava/lang/String;ZZ)V")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(begin.instructions.toArray()).anyMatch(n->n instanceof LdcInsnNode c && Long.valueOf(60_000).equals(c.cst)));
        ClassNode controller=read(Files.readAllBytes(Path.of("build/classes/java/main/dev/autostore/Controller.class")));
        MethodNode eligible=controller.methods.stream().filter(m->m.name.equals("canRecoverStorage")).findFirst().orElseThrow();
        assertTrue(calls(eligible).containsAll(Set.of("recoveryBlocker","hasDepositOutput","getCurrentServer","getSingleplayerServer")));
        assertTrue(Arrays.stream(eligible.instructions.toArray()).anyMatch(n->n instanceof FieldInsnNode f && f.name.equals("WAITING_STORAGE")));
        MethodNode shared=controller.methods.stream().filter(m->m.name.equals("recoveryBlocker") && m.desc.startsWith("(L")).findFirst().orElseThrow();
        Set<String> guards=new HashSet<>();
        for(AbstractInsnNode n:shared.instructions)if(n instanceof FieldInsnNode f)guards.add(f.name);
        assertTrue(guards.containsAll(Set.of("before","manuallyPaused","ownsScreen","MINE")));
        assertTrue(calls(shared).containsAll(Set.of("waiting","isAlive","miningActive","getCarried")));
    }

}
