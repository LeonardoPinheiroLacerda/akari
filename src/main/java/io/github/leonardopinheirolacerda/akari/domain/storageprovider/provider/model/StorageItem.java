package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model;

public record StorageItem(
        String path,
        String name,
        Long size,
        String mimeType,
        String modTime,
        boolean isDir,
        String id
) {}
