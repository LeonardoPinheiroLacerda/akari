package io.github.leonardopinheirolacerda.akari.utils;

import java.util.Set;

/**
 * Classificação de arquivo por extensão de nome — usada pela ingestão pra filtrar quais itens
 * de um storage provider contam como vídeo.
 *
 * <p>Pura e sem estado: nenhum método toca disco, só processa string.
 */
public final class FileNameUtils {

    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mkv", "mp4", "avi", "mov", "wmv", "webm", "ts", "m2ts", "flv");

    private FileNameUtils() {
    }

    /**
     * Extrai a extensão de um nome de arquivo (sem o ponto).
     *
     * @param name nome do arquivo
     * @return a extensão, sem o ponto e no case original; {@code null} se não houver ponto no
     *         nome
     */
    public static String extensionOf(String name) {
        final int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? null : name.substring(lastDot + 1);
    }

    /**
     * Confirma se um arquivo é de vídeo, pela extensão (case-insensitive).
     *
     * @param name nome do arquivo
     * @return {@code true} se a extensão (minúscula) estiver na lista de extensões de vídeo
     *         conhecidas ({@code mkv}, {@code mp4}, {@code avi}, {@code mov}, {@code wmv},
     *         {@code webm}, {@code ts}, {@code m2ts}, {@code flv})
     */
    public static boolean isVideoFile(String name) {
        final String extension = extensionOf(name);
        return extension != null && VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }

}
