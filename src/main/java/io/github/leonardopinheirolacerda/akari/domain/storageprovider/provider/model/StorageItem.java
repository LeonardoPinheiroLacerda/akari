package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model;

import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import java.util.ArrayList;
import java.util.List;

public record StorageItem(
        String path,
        String name,
        Long size,
        String mimeType,
        String modTime,
        boolean isDir,
        String id
) {

    /**
     * Acrescenta um {@link StorageItem} sintético representando o próprio {@code rootPath} à
     * lista — usado quando o rootPath consultado não aparece na listagem do provider (ele é o
     * ponto de partida, não um filho de si mesmo).
     *
     * @param rootPath path consultado, ou vazio pra raiz do provider
     * @param items itens já listados
     * @return a lista com o item sintético acrescentado, ou a própria lista se {@code rootPath}
     *         for vazio
     */
    public static List<StorageItem> withSyntheticRoot(String rootPath, List<StorageItem> items) {
        if (rootPath.isEmpty()) {
            return items;
        }

        final List<StorageItem> withRoot = new ArrayList<>(items);

        final String name = DirectoryUtils.lastSegmentOf(rootPath);
        final StorageItem storageItem = new StorageItem(rootPath, name, null, null, null, true, null);

        withRoot.add(storageItem);

        return withRoot;
    }

    /**
     * Ordena por profundidade de path ascendente — garante que o pai sempre apareça antes do
     * filho.
     *
     * @param items itens a ordenar
     * @return a lista ordenada
     */
    public static List<StorageItem> sortedByDepth(List<StorageItem> items) {
        return items.stream()
                .sorted((a, b) -> Integer.compare(DirectoryUtils.depthOf(a.path()), DirectoryUtils.depthOf(b.path())))
                .toList();
    }

    /**
     * Filtra só os itens cujo pai direto é {@code parentPath}.
     *
     * @param items itens a filtrar
     * @param parentPath path do pai
     * @return os filhos diretos
     */
    public static List<StorageItem> directChildrenOf(List<StorageItem> items, String parentPath) {
        return items.stream()
                .filter(item -> DirectoryUtils.parentOf(item.path()).equals(parentPath))
                .toList();
    }

}
