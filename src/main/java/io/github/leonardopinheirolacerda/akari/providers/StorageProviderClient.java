package io.github.leonardopinheirolacerda.akari.providers;

import io.github.leonardopinheirolacerda.akari.providers.models.StorageItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface StorageProviderClient {

    List<StorageItem> listChildren(String path);

    List<StorageItem> listChildDirectories(String path);

    List<StorageItem> listChildVideos(String path);

    List<StorageItem> listTree(String path);

    Long downloadFile(String remoteFilePath, String localDestinationDir, String localFileName);

    boolean checkConnection();

    Optional<Path> resolveLocalPath(String relativePath);
}