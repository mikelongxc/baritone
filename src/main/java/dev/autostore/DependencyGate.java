package dev.autostore;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Snapshot version strings are not unique; refuse a different runtime binary with the same name. */
final class DependencyGate {
    private DependencyGate() {}
    static void check() {
        check("meteor-client","0a80eb3dcfe3c0e64bc9629bdde444c4a0a187033cd5c0d608474fbac745e5fd");
    }
    private static void check(String id,String expected) {
        try {
            var paths=FabricLoader.getInstance().getModContainer(id).orElseThrow().getOrigin().getPaths();
            if(paths.size()!=1 || !Files.isRegularFile(paths.getFirst()))throw new IllegalStateException();
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(paths.getFirst())));
            if(!expected.equals(actual))throw new IllegalStateException();
        } catch(Exception ignored) {
            throw new IllegalStateException("AutoStore requires the exact archived Minecraft 26.3 dependency binaries; see DEPENDENCIES.md. No job was started.");
        }
    }
}
