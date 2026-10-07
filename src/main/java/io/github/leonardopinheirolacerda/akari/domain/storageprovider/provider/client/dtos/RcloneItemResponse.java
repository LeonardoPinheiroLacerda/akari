package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um item (arquivo ou diretório) retornado por {@code /operations/list}. As anotações
 * {@link JsonProperty} traduzem os nomes PascalCase que o rclone usa no JSON.
 *
 * @param path path relativo à raiz do filesystem
 * @param name nome do item (última parte do path)
 * @param size tamanho em bytes; para diretórios geralmente {@code -1}
 * @param mimeType MIME type detectado pelo rclone (ex.: {@code video/mp4}); {@code null} se
 *                 não detectou
 * @param modTime data da última modificação em formato ISO-8601 retornado pelo rclone
 * @param isDir {@code true} se o item é um diretório
 * @param id identificador nativo do provider (ex.: id do MEGA); pode ser {@code null}
 *           dependendo do provider
 */
public record RcloneItemResponse(
        @JsonProperty("Path") String path,
        @JsonProperty("Name") String name,
        @JsonProperty("Size") Long size,
        @JsonProperty("MimeType") String mimeType,
        @JsonProperty("ModTime") String modTime,
        @JsonProperty("IsDir") boolean isDir,
        @JsonProperty("ID") String id
) {
}
