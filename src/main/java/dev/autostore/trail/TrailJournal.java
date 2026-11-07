package dev.autostore.trail;

import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Local-only, account/server/dimension-scoped recovery. Never serialize an address or account name. */
public final class TrailJournal {
    public int schema=1;
    public String scope,boxType,boxIdentity;
    public Integer boxX,boxY,boxZ;
    public int boxesBefore,boxRockets,searches;
    public boolean placed;
    public double headingX,headingZ;
    public Map<Long,TrailRoute.Kind> evidence=new LinkedHashMap<>();
    public Set<Long> visited=new LinkedHashSet<>();
    public Integer searchX,searchZ;
    public boolean hasBox(){return boxX!=null;}
    public void clearBox(){boxX=boxY=boxZ=null;boxType=boxIdentity=null;placed=false;boxesBefore=boxRockets=0;}
    public static TrailJournal load(Path directory,String scope) throws Exception {
        Path file=path(directory,scope);
        if(!Files.exists(file)){TrailJournal j=new TrailJournal();j.scope=scope;return j;}
        if(Files.size(file)>2_000_000)throw new IllegalStateException("Trail checkpoint too large");
        TrailJournal j=new Gson().fromJson(Files.readString(file),TrailJournal.class);
        if(j==null || j.schema!=1 || !scope.equals(j.scope) || j.evidence==null || j.visited==null
            || !Double.isFinite(j.headingX) || !Double.isFinite(j.headingZ) || j.searches<0 || j.searches>8
            || j.evidence.size()>16384 || j.visited.size()>4096
            || j.hasBox() && (j.boxY==null || j.boxZ==null || j.boxType==null || j.boxIdentity==null || j.boxesBefore<1))
            throw new IllegalStateException("Invalid trail checkpoint");
        return j;
    }
    public void save(Path directory) throws Exception {
        Files.createDirectories(directory);privateMode(directory,"rwx------");
        Path temp=Files.createTempFile(directory,"trail-",".tmp");
        try {
            privateMode(temp,"rw-------");
            byte[] bytes=new Gson().toJson(this).getBytes(StandardCharsets.UTF_8);
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temp,path(directory,scope),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
    private static Path path(Path directory,String scope) {
        if(scope==null || !scope.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid trail scope");
        return directory.resolve(scope+".json");
    }
    private static void privateMode(Path path,String mode)throws Exception {
        try{Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(mode));}catch(UnsupportedOperationException ignored){}
    }
}
