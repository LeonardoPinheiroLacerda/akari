package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Cataloga as estratégias de transcode descobertas pelo CDI e as filtra por profile: dado um
 * {@link Profile}, devolve as {@link HlsTranscodeStrategy} que pertencem a ele.
 *
 * <p>Nenhum registro manual — adicionar uma estratégia bean já a coloca no catálogo.
 */
@ApplicationScoped
public class TranscodeStrategyCatalog {

    @Inject
    Instance<HlsTranscodeStrategy> strategies;

    /**
     * @param profile profile de transcode alvo
     * @return estratégias que pertencem ao profile; vazio se nenhuma se declara para ele
     */
    public List<HlsTranscodeStrategy> strategiesFor(Profile profile) {
        return strategies.stream()
                .filter(strategy -> strategy.profiles().contains(profile))
                .toList();
    }

}
