package io.github.leonardopinheirolacerda.akari.utils;

/**
 * Manipulação textual de paths relativos (sempre separados por {@code /}, nunca
 * {@code File.separator}) — os paths do catalog/storage provider são lógicos, não caminhos
 * reais de filesystem.
 *
 * <p>Pura e sem estado: nenhum método toca disco, só processa string.
 */
public final class DirectoryUtils {

    private DirectoryUtils() {
    }

    /**
     * Devolve o path pai, removendo o último segmento.
     *
     * @param path path relativo; {@code null} é tratado como raiz
     * @return o path sem o último segmento; string vazia se {@code path} for {@code null} ou já
     *         for o primeiro nível (sem {@code /})
     */
    public static String parentOf(String path) {
        if (path == null) {
            return "";
        }

        final int lastSlash = path.lastIndexOf('/');

        if (lastSlash < 0) {
            return "";
        }

        return path.substring(0, lastSlash);
    }

    /**
     * Concatena um path pai com um nome, separados por {@code /}.
     *
     * @param parentPath path pai; string vazia gera um path só com {@code name} prefixado por
     *                   {@code /}
     * @param name segmento a acrescentar
     * @return {@code parentPath + "/" + name}
     */
    public static String join(String parentPath, String name) {
        return parentPath + "/" + name;
    }

    /**
     * Devolve o último segmento de um path.
     *
     * @param path path relativo
     * @return o que vem depois da última {@code /}; o próprio {@code path} se não houver {@code /}
     */
    public static String lastSegmentOf(String path) {
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? path : path.substring(lastSlash + 1);
    }

    /**
     * Conta quantos segmentos um path tem.
     *
     * @param path path relativo
     * @return {@code 0} se {@code path} for vazio; senão, a quantidade de segmentos separados
     *         por {@code /}
     */
    public static int depthOf(String path) {
        return path.isEmpty() ? 0 : path.split("/").length;
    }

}
