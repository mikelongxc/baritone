package dev.autostore.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PackagingTest {
    @Test void mandatoryNestedLibraryHasFabricDiscoveryMetadataAndItsStartupClasses() throws Exception {
        try(JarFile jar=new JarFile(System.getProperty("autostore.runtimeJar"))) {
            var mod=JsonParser.parseString(new String(jar.getInputStream(jar.getJarEntry("fabric.mod.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(System.getProperty("autostore.version"),mod.get("version").getAsString());
            String nested=mod.getAsJsonArray("jars").get(0).getAsJsonObject().get("file").getAsString();
            Map<String,byte[]> files=new HashMap<>();
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(jar.getInputStream(jar.getJarEntry(nested)).readAllBytes()))) {
                for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()) files.put(e.getName(),zip.readAllBytes());
            }
            assertNotNull(files.get("fabric.mod.json"),"A plain nested jar is not discoverable by Fabric");
            var library=JsonParser.parseString(new String(files.get("fabric.mod.json"),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("1.6.0",mod.getAsJsonObject("depends").get(library.get("id").getAsString()).getAsString());
            assertNotNull(files.get("dev/babbaj/pathfinder/NetherPathfinder.class"));
            assertNotNull(files.get("dev/babbaj/pathfinder/Octree.class"));
        }
    }
}
