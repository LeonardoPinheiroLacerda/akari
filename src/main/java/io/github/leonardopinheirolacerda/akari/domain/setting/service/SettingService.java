package io.github.leonardopinheirolacerda.akari.domain.setting.service;

import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.setting.mapper.SettingMapper;
import io.github.leonardopinheirolacerda.akari.domain.setting.model.Setting;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Preferências administráveis do akari: um conjunto de chave/valor seedado por migração, lido
 * em runtime por outros domínios (TMDB, cache, playback) via os getters tipados abaixo.
 *
 * <p>Os getters tipados lançam {@link ResourceNotFoundException} se a linha da preferência não
 * existir no banco — o que só acontece se ela nunca foi seedada por migração, não se o
 * {@code value} estiver nulo/em branco (preferências sem valor configurado, como
 * {@code cache.dir} antes do admin preencher, têm linha existente com {@code value} nulo; quem
 * chama o getter decide se isso é um erro, ex.: {@code IntegrationNotConfiguredException}).
 */
@ApplicationScoped
public class SettingService {

    @Inject
    SettingMapper settingMapper;

    /**
     * Lista as preferências, opcionalmente filtrando por nome.
     *
     * @param name substring case-insensitive da chave a filtrar; {@code null} lista todas
     * @return preferências ordenadas pela chave, com valores secretos mascarados
     *         ({@code "***"})
     */
    public List<SettingResponse> listSettings(String name) {
        final List<Setting> settings = name == null
                ? Setting.listAll()
                : Setting.findByName(name);

        final List<SettingResponse> responses = settingMapper.toResponseList(settings);

        responses.forEach(SettingService::maskSecretValue);

        Log.infof("Listadas %d preferências", responses.size());

        return responses;
    }

    /**
     * Atualiza o valor de uma preferência existente.
     *
     * @param key chave da preferência a atualizar
     * @param updateSettingRequest novo valor
     * @return a preferência atualizada, com o valor secreto mascarado se aplicável
     * @throws ResourceNotFoundException se a chave não existir
     */
    @Transactional
    public SettingResponse updateSetting(String key, UpdateSettingRequest updateSettingRequest) {
        Log.infof("Atualizando a preferência %s", key);

        final Setting setting = findOrThrow(key);

        setting.value = updateSettingRequest.getValue();

        // Força o flush aqui: @UpdateTimestamp só atualiza o updatedAt em memória durante o
        // flush, que sem isso só aconteceria no commit da transação — depois da resposta já
        // ter sido montada com o valor antigo.
        Panache.flush();

        Log.infof("Preferência %s atualizada com sucesso", key);

        final SettingResponse response = settingMapper.toResponse(setting);
        maskSecretValue(response);
        return response;
    }

    /**
     * Lê a preferência {@code anilist.cache.ttl}.
     *
     * @return o TTL do cache de respostas da AniList
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public Duration getAnilistCacheTtl() {
        return Duration.parse(findOrThrow("anilist.cache.ttl").value);
    }

    /**
     * Lê a preferência {@code tmdb.cache.ttl}.
     *
     * @return o TTL do cache de respostas do TMDB
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public Duration getTmdbCacheTtl() {
        return Duration.parse(findOrThrow("tmdb.cache.ttl").value);
    }

    /**
     * Lê a preferência {@code tmdb.api-key}.
     *
     * @return o token de API do TMDB; {@code null} se ainda não configurado
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getTmdbApiKey() {
        return findOrThrow("tmdb.api-key").value;
    }

    /**
     * Confirma se a preferência {@code tmdb.api-key} está preenchida.
     *
     * @return {@code true} se há um token cadastrado
     */
    public boolean isTmdbConfigured() {
        final String apiKey = getTmdbApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Lê a preferência {@code tmdb.default-language}.
     *
     * @return o idioma padrão usado nas buscas do TMDB
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getTmdbDefaultLanguage() {
        return findOrThrow("tmdb.default-language").value;
    }

    /**
     * Lê a preferência {@code tmdb.image.logo-language}.
     *
     * @return o idioma preferido pro logo do título no TMDB
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getTmdbImageLogoLanguage() {
        return findOrThrow("tmdb.image.logo-language").value;
    }

    /**
     * Lê a preferência {@code tmdb.image.backdrop-language}.
     *
     * @return o idioma preferido pro backdrop no TMDB
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getTmdbImageBackdropLanguage() {
        return findOrThrow("tmdb.image.backdrop-language").value;
    }

    /**
     * Lê a preferência {@code tmdb.include-adult}.
     *
     * @return {@code true} se buscas no TMDB devem incluir conteúdo adulto
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public boolean isTmdbIncludeAdult() {
        return Boolean.parseBoolean(findOrThrow("tmdb.include-adult").value);
    }

    /**
     * Lê a preferência {@code cache.max-size-bytes}.
     *
     * @return o orçamento máximo de espaço do cache local, em bytes
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public Long getCacheMaxSizeBytes() {
        return Long.parseLong(findOrThrow("cache.max-size-bytes").value);
    }

    /**
     * Lê a preferência {@code cache.dir}.
     *
     * @return o diretório do cache local; {@code null} se ainda não configurado
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getCacheDir() {
        return findOrThrow("cache.dir").value;
    }

    /**
     * Lê a preferência {@code playback.transcode.hardware-acceleration}.
     *
     * @return o método de aceleração de hardware a usar no transcode de playback
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getPlaybackTranscodeHardwareAcceleration() {
        return findOrThrow("playback.transcode.hardware-acceleration").value;
    }

    /**
     * Lê a preferência {@code playback.session.heartbeat-ttl}.
     *
     * @return o tempo de vida de uma sessão de playback sem heartbeat do cliente
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public Duration getPlaybackSessionHeartbeatTtl() {
        return Duration.parse(findOrThrow("playback.session.heartbeat-ttl").value);
    }

    /**
     * Lê a preferência {@code playback.work.base-dir}.
     *
     * @return o diretório base onde sessões de playback gravam seus arquivos de trabalho
     * @throws ResourceNotFoundException se a preferência não existir
     */
    public String getPlaybackWorkBaseDir() {
        return findOrThrow("playback.work.base-dir").value;
    }

    private Setting findOrThrow(String key) {
        return Setting.find(key)
                .orElseThrow(() -> {
                    Log.warnf("Preferência %s não encontrada", key);
                    return new ResourceNotFoundException("Não foi possível localizar uma preferência com a chave informada");
                });
    }

    private static void maskSecretValue(SettingResponse response) {
        if (Boolean.TRUE.equals(response.getSecret())) {
            response.setValue("***");
        }
    }

}
