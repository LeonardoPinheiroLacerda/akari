package io.github.leonardopinheirolacerda.akari.providers.models;

public record StorageItem(
        String path,
        String name,
        Long size,
        String mimeType,
        String modTime,
        boolean isDir,
        String id
) {}
