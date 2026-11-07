package dev.autostore.trail;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.level.ChunkPos;
import java.lang.reflect.Field;
import java.util.*;

/** Read bounded local evidence from the installed Trouser Streak. No files or detector settings are changed. */
public final class TrouserTrail {
    private final Module module;
    private final Field fresh,old,legacy,updated;
    public TrouserTrail() throws Exception {
        Class<?> type=Class.forName("pwn.noobs.trouserstreak.modules.NewerNewChunks");
        module=Modules.get().get(type.asSubclass(Module.class));
        if(module==null)throw new IllegalStateException("Trouser Streak NewerNewChunks is missing");
        fresh=field(type,"newChunks");old=field(type,"oldChunks");
        legacy=field(type,"OldGenerationOldChunks");updated=field(type,"beingUpdatedOldChunks");
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    public void enable(){if(!module.isActive())module.toggle();}
    public boolean active(){return module.isActive();}
    @SuppressWarnings("unchecked") public void sample(TrailRoute route,TrailRoute.Cell center)throws Exception {
        sampleSets(route,center,(Set<ChunkPos>)fresh.get(module),(Set<ChunkPos>)old.get(module),
            (Set<ChunkPos>)legacy.get(module),(Set<ChunkPos>)updated.get(module));
    }
    public static void sampleSets(TrailRoute route,TrailRoute.Cell center,Set<ChunkPos> fresh,Set<ChunkPos> old,Set<ChunkPos> legacy,Set<ChunkPos> updated) {
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++) {
            ChunkPos p=new ChunkPos(center.x()+x,center.z()+z);
            TrailRoute.Cell cell=new TrailRoute.Cell(p.x(),p.z());
            if(fresh.contains(p))route.observe(cell,TrailRoute.Kind.NEW);
            else if(old.contains(p)||legacy.contains(p)||updated.contains(p))route.observe(cell,TrailRoute.Kind.OLD);
        }
    }
}
