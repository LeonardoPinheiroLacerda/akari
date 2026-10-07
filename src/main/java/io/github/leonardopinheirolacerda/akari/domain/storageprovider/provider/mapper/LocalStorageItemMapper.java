package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalStorageItemMapper {

    public StorageItem toStorageItem(Path root, Path entry) {
        final boolean isDir = Files.isDirectory(entry);
        final String relative = root.relativize(entry).toString();

        return new StorageItem(
                relative,
                entry.getFileName().toString(),
                isDir ? null : sizeOf(entry),
                isDir ? null : mimeTypeOf(entry),
                modTimeOf(entry),
                isDir,
                null
        );
    }

    private static Long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return null;
        }
    }

    private static String modTimeOf(Path entry) {
        try {
            return Files.getLastModifiedTime(entry).toInstant().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String mimeTypeOf(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException e) {
            return null;
        }
    }

}