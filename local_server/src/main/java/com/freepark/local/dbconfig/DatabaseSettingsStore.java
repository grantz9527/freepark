package com.freepark.local.dbconfig;

import java.nio.file.Path;

public final class DatabaseSettingsStore {

    public static final String DEFAULT_RELATIVE_PATH = "data/database.json";

    private DatabaseSettingsStore() {
    }

    public static Path resolveFile(String configured) {
        Path path = (configured == null || configured.isBlank())
                ? Path.of(DEFAULT_RELATIVE_PATH)
                : Path.of(configured.trim());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir", ".")).resolve(path).normalize();
        }
        return path;
    }
}
