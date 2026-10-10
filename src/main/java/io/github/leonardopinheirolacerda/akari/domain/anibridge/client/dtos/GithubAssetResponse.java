package io.github.leonardopinheirolacerda.akari.domain.anibridge.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um asset de uma release do GitHub.
 *
 * @param name nome do arquivo (ex.: {@code mappings.min.json})
 * @param browserDownloadUrl URL de download direto do asset
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubAssetResponse(
        String name,
        @JsonProperty("browser_download_url") String browserDownloadUrl
) {
}