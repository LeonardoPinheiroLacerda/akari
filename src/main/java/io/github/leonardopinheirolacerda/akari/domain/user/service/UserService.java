package io.github.leonardopinheirolacerda.akari.domain.user.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.UserRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.UserResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.WatchProgress;
import io.github.leonardopinheirolacerda.akari.api.dto.WatchlistItemRequest;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeOverrideService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.user.mapper.UserMapper;
import io.github.leonardopinheirolacerda.akari.domain.user.model.User;
import io.github.leonardopinheirolacerda.akari.domain.user.model.WatchHistory;
import io.github.leonardopinheirolacerda.akari.domain.user.model.WatchlistItem;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Perfis de usuário, o histórico de visualização de cada um (alimentado pelo heartbeat do
 * playback) e a lista de títulos.
 */
@ApplicationScoped
public class UserService {

    /** Fração da duração a partir da qual o arquivo conta como assistido. */
    private static final double WATCHED_THRESHOLD = 0.9;

    @Inject
    UserMapper userMapper;

    @Inject
    AnimeService animeService;

    @Inject
    AnimeOverrideService animeOverrideService;

    /**
     * Lista os usuários.
     *
     * @return os usuários, por {@code id}
     */
    public List<UserResponse> listUsers() {
        final List<User> users = User.listAllOrdered();
        return userMapper.toResponseList(users);
    }

    /**
     * Cadastra um usuário.
     *
     * @param userRequest nome do usuário
     * @return o usuário criado
     */
    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        final User user = userMapper.toModel(userRequest);

        user.persist();

        Log.infof("Usuário %d cadastrado", user.id);

        return userMapper.toResponse(user);
    }

    /**
     * Remove o usuário. O banco apaga em cascata o histórico e a lista de títulos dele, e
     * desvincula as sessões de playback em andamento ({@code ON DELETE SET NULL}), que seguem
     * sem usuário até expirar — assim o ffmpeg delas é encerrado pela varredura normal, em vez
     * de ficar órfão.
     *
     * @param userId id do usuário
     * @throws ResourceNotFoundException se o usuário não existir
     */
    @Transactional
    public void deleteUser(Integer userId) {
        final User user = findOrThrow(userId);

        user.delete();

        Log.infof("Usuário %d removido", userId);
    }

    /**
     * Pagina a lista de títulos do usuário, do adicionado mais recentemente pro mais antigo.
     *
     * @param userId id do usuário
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes, com título e thumbnail já resolvidos
     * @throws ResourceNotFoundException se o usuário não existir
     */
    public AnimeBindingSummaryPage listUserWatchlist(Integer userId, Integer page, Integer size) {
        findOrThrow(userId);

        final PageResult<WatchlistItem> pageResult = WatchlistItem.findPageByUser(userId, page, size);

        return new AnimeBindingSummaryPage()
                .page(pageResult.page())
                .size(pageResult.size())
                .totalElements(pageResult.totalElements())
                .totalPages(pageResult.totalPages())
                .data(pageResult
                        .data()
                        .stream()
                        .map(item -> animeOverrideService.toBindingSummary(item.anime))
                        .toList());
    }

    /**
     * Adiciona um título à lista do usuário. Idempotente: se o título já está na lista, não faz
     * nada.
     *
     * @param userId id do usuário
     * @param watchlistItemRequest anime a adicionar
     * @throws ResourceNotFoundException se o usuário ou o anime não existir
     */
    @Transactional
    public void addUserWatchlistItem(Integer userId, WatchlistItemRequest watchlistItemRequest) {
        final User user = findOrThrow(userId);
        final Anime anime = animeService.findOrThrow(watchlistItemRequest.getAnilistId());

        if (WatchlistItem.findByUserAndAnime(userId, anime.anilistId).isPresent()) {
            return;
        }

        final WatchlistItem item = new WatchlistItem();

        item.user = user;
        item.anime = anime;

        item.persist();

        Log.infof("Anime %d adicionado à lista do usuário %d", anime.anilistId, userId);
    }

    /**
     * Remove um título da lista do usuário. Idempotente: se o título não está na lista, não faz
     * nada.
     *
     * @param userId id do usuário
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException se o usuário não existir
     */
    @Transactional
    public void removeUserWatchlistItem(Integer userId, Integer anilistId) {
        findOrThrow(userId);

        final Optional<WatchlistItem> item = WatchlistItem.findByUserAndAnime(userId, anilistId);

        if (item.isEmpty()) {
            return;
        }

        item.get().delete();

        Log.infof("Anime %d removido da lista do usuário %d", anilistId, userId);
    }

    /**
     * Diz se um título está na lista do usuário — usado pela tela do anime (franquia).
     *
     * @param userId id do usuário
     * @param anilistId id do anime na AniList
     * @return {@code true} se o título está na lista
     * @throws ResourceNotFoundException se o usuário não existir
     */
    public boolean isInWatchlist(Integer userId, Integer anilistId) {
        findOrThrow(userId);

        return WatchlistItem.findByUserAndAnime(userId, anilistId)
                .isPresent();
    }

    /**
     * Grava a posição atual do usuário num arquivo, criando a linha do histórico no primeiro
     * heartbeat. Marca como assistido quando a posição chega a 90% da duração — e, uma vez
     * assistido, não desmarca.
     *
     * @param user usuário que está assistindo
     * @param videoFile arquivo sendo reproduzido
     * @param positionSeconds posição atual, em segundos a partir do início da mídia
     * @param durationSeconds duração da mídia; {@code null} se desconhecida (nunca marca como
     *                        assistido)
     */
    @Transactional
    public void recordWatchProgress(User user,
                                    VideoFile videoFile,
                                    Double positionSeconds,
                                    Double durationSeconds) {

        final WatchHistory watchHistory = WatchHistory.findByUserAndFile(user.id, videoFile.id)
                .orElseGet(() -> newWatchHistory(user, videoFile));

        watchHistory.positionSeconds = positionSeconds;
        watchHistory.durationSeconds = durationSeconds;
        watchHistory.lastWatchedAt = OffsetDateTime.now();

        if (durationSeconds != null && positionSeconds >= durationSeconds * WATCHED_THRESHOLD) {
            watchHistory.watched = true;
        }

        watchHistory.persist();
    }

    /**
     * Progresso do usuário em cada arquivo informado — usado pela listagem de episódios pra
     * marcar os assistidos e onde o usuário parou.
     *
     * @param userId id do usuário
     * @param fileIds ids dos arquivos
     * @return o progresso por id de arquivo; arquivo que o usuário nunca abriu fica de fora
     * @throws ResourceNotFoundException se o usuário não existir
     */
    public Map<Integer, WatchProgress> findWatchProgressByFiles(Integer userId, List<Integer> fileIds) {
        findOrThrow(userId);

        return WatchHistory.findByUserAndFiles(userId, fileIds)
                .stream()
                .collect(Collectors.toMap(
                        watchHistory -> watchHistory.videoFile.id,
                        userMapper::toWatchProgress
                ));
    }

    /**
     * Busca o usuário pelo id ou lança 404. Usado também por outros domínios (playback, anime)
     * que recebem um {@code userId}.
     *
     * @param userId id do usuário
     * @return o usuário
     * @throws ResourceNotFoundException se o usuário não existir
     */
    public User findOrThrow(Integer userId) {
        return User.find(userId)
                .orElseThrow(() -> {
                    Log.warnf("Usuário %d não encontrado", userId);
                    return new ResourceNotFoundException(
                            "Não foi possível localizar um usuário com o id informado");
                });
    }

    private WatchHistory newWatchHistory(User user, VideoFile videoFile) {
        final WatchHistory watchHistory = new WatchHistory();

        watchHistory.user = user;
        watchHistory.videoFile = videoFile;
        watchHistory.watched = false;

        return watchHistory;
    }

}
