package com.dilaw.mohe.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

/** Resolves paths under a storage root and rejects path-traversal attempts. */
public final class SafePathUtils {

    private SafePathUtils() {
    }

    public static Path resolveUnderRoot(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: path outside storage root");
        }
        return resolved;
    }

    public static Path resolveFilenameUnderRoot(Path root, String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        return resolveUnderRoot(root, filename);
    }
}
