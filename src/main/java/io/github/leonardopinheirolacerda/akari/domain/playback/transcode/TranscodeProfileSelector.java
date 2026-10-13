package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Escolhe a estratégia de transcode vencedora para um vídeo: dentre as estratégias do profile,
 * pega a de maior {@link HlsTranscodeStrategy#score(MediaInfoResponse)}.
 *
 * <p>Estratégias que se abstêm ({@code score <= 0}) são ignoradas. Empate no topo é resolvido,
 * por ora, pela ordem de descoberta do CDI.
 */
@ApplicationScoped
public class TranscodeProfileSelector {

    @Inject
    TranscodeStrategyCatalog catalog;

    /**
     * @param profile profile de transcode alvo
     * @param probe metadados técnicos do vídeo que alimentam a nota
     * @return a estratégia de maior nota do profile; {@link Optional#empty()} se nenhuma pontua
     */
    public Optional<HlsTranscodeStrategy> select(Profile profile, MediaInfoResponse probe) {
        HlsTranscodeStrategy best = null;
        int bestScore = 0;

        for (HlsTranscodeStrategy strategy : catalog.strategiesFor(profile)) {
            final int score = strategy.score(probe);

            if (score > bestScore) {
                bestScore = score;
                best = strategy;
            }
        }

        return Optional.ofNullable(best);
    }

}
