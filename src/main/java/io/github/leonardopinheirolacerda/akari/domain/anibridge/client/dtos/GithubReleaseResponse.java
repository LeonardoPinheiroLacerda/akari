package io.github.leonardopinheirolacerda.akari.domain.anibridge.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A release mais recente do {@code anibridge/anibridge-mappings}.
 *
 * @param tagName tag da release (hoje sempre {@code v3} — o workflow upstream atualiza a mesma
 *                release em vez de criar uma nova por dia)
 * @param publishedAt data em que a release foi publicada originalmente — fica congelada entre
 *                     os refreshes diários do workflow upstream, não serve pra detectar mudança
 * @param updatedAt data da última atualização da release — é o que de fato muda a cada refresh
 *                   do workflow upstream; usado pra decidir se vale re-baixar o asset
 * @param assets arquivos da release, incluindo o {@code mappings.min.json}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubReleaseResponse(
        @JsonProperty("tag_name") String tagName,
        @JsonProperty("published_at") OffsetDateTime publishedAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        List<GithubAssetResponse> assets
) {
}