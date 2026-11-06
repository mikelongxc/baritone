package dev.autostore.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ProfileStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    public ProfileStore(Path directory) { this.directory = directory; }
    public static String scope(String serverOrWorld, String account, String dimension) {
        try {
            String raw = serverOrWorld + "\0" + account + "\0" + dimension;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Scope hashing unavailable"); }
    }
    public Profile load(String scope) {
        try {
            Path path = path(scope);
            if (!Files.exists(path)) { Profile p = new Profile(); p.scope = scope; return p; }
            if (Files.size(path) > 1_000_000) throw new IOException();
            Profile p = JSON.fromJson(Files.readString(path), Profile.class);
            if (p == null || (p.schema != 1 && p.schema != 2) || !Policy.sameScope(scope,p.scope)) throw new IOException();
            if (p.schema == 1) {
                p.schema = 2;
                p.quarry = null; p.corridor = null;
                p.reserveSlots = 1;
                if (p.protectedSlots.equals(new java.util.LinkedHashSet<>(java.util.List.of(0,1,2,3,4,5,6,7,8)))) p.protectedSlots.clear();
            }
            p.checkpoint = "PAUSED"; p.reason = "Loaded checkpoint; inspect inventory and resume manually";
            return p;
        } catch (Exception e) { throw new Loop.SafetyException("Profile unreadable; repair it before starting"); }
    }
    public void save(Profile profile) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            privateMode(directory, "rwx------");
            Path target = path(profile.scope);
            temporary = Files.createTempFile(directory,"checkpoint-", ".tmp");
            privateMode(temporary, "rw-------");
            byte[] bytes = JSON.toJson(profile).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel dir = FileChannel.open(directory, StandardOpenOption.READ)) { dir.force(true); }
            catch (UnsupportedOperationException | IOException ignored) { /* Directory fsync not supported on every OS. */ }
        } catch (Exception e) { throw new Loop.SafetyException("Atomic checkpoint failed; storage may be read-only or full"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }
    private Path path(String scope) {
        if (scope == null || !scope.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid scope key");
        return directory.resolve(scope + ".json");
    }
    private static void privateMode(Path path, String mode) throws IOException {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode)); }
        catch (UnsupportedOperationException ignored) { /* Use inherited ACLs on non-POSIX systems. */ }
    }
}
