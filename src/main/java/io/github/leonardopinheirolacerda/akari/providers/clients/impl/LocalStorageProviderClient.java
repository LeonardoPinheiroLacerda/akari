package io.github.leonardopinheirolacerda.akari.providers.clients.impl;

import io.github.leonardopinheirolacerda.akari.providers.clients.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.mappers.LocalStorageItemMapper;
import io.github.leonardopinheirolacerda.akari.providers.models.StorageItem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class LocalStorageProviderClient implements StorageProviderClient {

    private final Path root;
    private final LocalStorageItemMapper mapper = new LocalStorageItemMapper();

    public LocalStorageProviderClient(Map<String, String> config) {
        this.root = Path.of(config.get("rootPath"));
    }

    @Override
    public List<StorageItem> listChildren(String path) {
        final Path dir = root.resolve(path);

        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .map(entry -> mapper.toStorageItem(root, entry))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar diretório local: " + dir, e);
        }
    }

    @Override
    public List<StorageItem> listChildDirectories(String path) {
        return listChildren(path)
                .stream()
                .filter(StorageItem::isDir)
                .toList();
    }

    @Override
    public List<StorageItem> listChildVideos(String path) {
        return listChildren(path)
                .stream()
                .filter(item -> !item.isDir())
                .filter(item -> item.mimeType() != null && item.mimeType().startsWith("video/"))
                .toList();
    }

    @Override
    public List<StorageItem> listTree(String path) {
        final Path start = root.resolve(path);

        if (!Files.isDirectory(start)) {
            return List.of();
        }

        try (Stream<Path> tree = Files.walk(start)) {
            return tree
                    .filter(entry -> !entry.equals(start))
                    .map(entry -> mapper.toStorageItem(root, entry))
                    .toList();

        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao varrer a árvore local: " + start, e);
        }
    }

    /** Provider local não usa cache — nada a baixar. */
    @Override
    public Long downloadFile(String remoteFilePath, String localDestinationDir, String localFileName) {
        throw new UnsupportedOperationException("Provider local não usa cache; download não se aplica");
    }

    @Override
    public boolean checkConnection() {
        return Files.isDirectory(root);
    }

    @Override
    public Optional<Path> resolveLocalPath(String relativePath) {
        final Path resolved = root.resolve(relativePath);
        
        return Files.isRegularFile(resolved) 
                ? Optional.of(resolved) 
                : Optional.empty();
    }
}
