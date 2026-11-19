package com.itheima.bolee.insurance.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Append-only deletion journal stored outside database backups so restores can replay deletions. */
@Component
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AiDeletionLedger {
    private static final Object JVM_LOCK = new Object();
    private final Path path;

    public AiDeletionLedger(@Value("${ai.deletion-ledger.path:}") String configuredPath) {
        if (!StringUtils.hasText(configuredPath)) {
            throw new IllegalStateException("AI_DELETION_LEDGER_PATH must point to durable storage outside database backups");
        }
        this.path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
    }

    /** Persists and fsyncs a tombstone before the database acknowledges deletion. */
    public void record(String agnoSessionId) {
        validateId(agnoSessionId);
        synchronized (JVM_LOCK) {
            try {
                createParent();
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.READ);
                     FileLock ignored = channel.lock()) {
                    channel.position(channel.size());
                    ByteBuffer bytes = StandardCharsets.US_ASCII.encode(agnoSessionId + "\n");
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
                restrictPermissions();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not durably record AI deletion", exception);
            }
        }
    }

    /** Returns unique session IDs and fails closed if any journal line is malformed. */
    public List<String> readAll() {
        synchronized (JVM_LOCK) {
            if (!Files.exists(path)) return Collections.emptyList();
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
                 FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
                if (channel.size() > Integer.MAX_VALUE) throw new IllegalStateException("AI deletion ledger is too large");
                ByteBuffer data = ByteBuffer.allocate((int) channel.size());
                while (data.hasRemaining() && channel.read(data) >= 0) { /* read entire journal */ }
                data.flip();
                String content = StandardCharsets.US_ASCII.decode(data).toString();
                Set<String> ids = new LinkedHashSet<>();
                String[] lines = content.split("\\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() && i == lines.length - 1) continue;
                    validateId(line);
                    ids.add(line.toLowerCase());
                }
                return new ArrayList<>(ids);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Could not read valid AI deletion ledger", exception);
            }
        }
    }

    private void createParent() throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private void restrictPermissions() {
        try {
            Set<PosixFilePermission> permissions = Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs are managed by the deployment; the ledger contains opaque UUIDs only.
        }
    }

    private static void validateId(String value) {
        try {
            if (value == null || value.length() != 36 || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Invalid Agno session UUID");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Agno session UUID", exception);
        }
    }
}
