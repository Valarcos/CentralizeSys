package com.centralizesys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralizes data path resolution to ensure consistent file locations
 * regardless of how the application is launched (Gradle vs IntelliJ).
 *
 * Paths are computed dynamically at startup based on the current working
 * directory,
 * looking for the project root by detecting the 'backend' directory marker.
 */
public class DataPathConfig {

    private static final Logger log = LoggerFactory.getLogger(DataPathConfig.class);
    private static final Path PROJECT_ROOT;

    private DataPathConfig() {
        throw new IllegalStateException("Utility class");
    }

    static {
        // Determine project root by checking current working directory structure
        Path cwd = Paths.get("").toAbsolutePath();

        // If we're running from 'backend/', go up one level to project root
        if (cwd.getFileName() != null && cwd.getFileName().toString().equals("backend")) {
            PROJECT_ROOT = cwd.getParent();
            log.info("Detected IntelliJ-style run. Project root: {}", PROJECT_ROOT);
        } else if (Files.exists(cwd.resolve("backend"))) {
            // We're already at project root (Gradle-style run)
            PROJECT_ROOT = cwd;
            log.info("Detected Gradle-style run. Project root: {}", PROJECT_ROOT);
        } else {
            // Fallback: assume current directory is the intended root
            PROJECT_ROOT = cwd;
            log.warn("Could not detect project structure. Using current directory as root: {}", PROJECT_ROOT);
        }
    }

    /**
     * Returns the detected project root directory.
     */
    public static Path getProjectRoot() {
        return PROJECT_ROOT;
    }

    /**
     * Resolves a relative path against the project root.
     *
     * @param relativePath Path relative to project root (e.g.,
     *                     "data/centralizesys.db")
     * @return Absolute Path object
     */
    public static Path resolve(String relativePath) {
        return PROJECT_ROOT.resolve(relativePath);
    }

    /**
     * Resolves a relative path and returns it as a String.
     * Useful for constants that need String values.
     *
     * @param relativePath Path relative to project root
     * @return Absolute path as String
     */
    public static String resolveString(String relativePath) {
        return resolve(relativePath).toString();
    }

    /**
     * Returns the absolute database URL for SQLite.
     */
    public static String getDatabaseUrl() {
        return "jdbc:sqlite:" + resolveString("data/centralizesys.db");
    }
}
