package io.github.leonardopinheirolacerda.akari.domain.user.resource;

import io.github.leonardopinheirolacerda.akari.api.UserApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.UserRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.UserResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.WatchlistItemRequest;
import io.github.leonardopinheirolacerda.akari.domain.user.service.UserService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Implementa {@link UserApi} — perfis de usuário, histórico de visualização e lista de títulos
 * em {@code /v1/users}.
 *
 * <p>Só delega pro {@link UserService}.
 */
@ApplicationScoped
public class UserResource implements UserApi {

    @Inject
    UserService userService;

    /**
     * {@code GET /v1/users} — lista os usuários.
     *
     * @return os usuários, com {@code 200 OK}
     */
    @Override
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    /**
     * {@code POST /v1/users} — cadastra um usuário.
     *
     * @param userRequest nome do usuário
     * @return o usuário criado, com {@code 201 Created}
     */
    @Override
    public UserResponse createUser(UserRequest userRequest) {
        return userService.createUser(userRequest);
    }

    /**
     * {@code DELETE /v1/users/{userId}} — remove o usuário, com o histórico e a lista dele.
     *
     * @param userId id do usuário
     * @throws ResourceNotFoundException (traduzida em 404) se o usuário não existir
     */
    @Override
    public void deleteUser(Integer userId) {
        userService.deleteUser(userId);
    }

    /**
     * {@code GET /v1/users/{userId}/watchlist} — lista de títulos do usuário, paginada.
     *
     * @param userId id do usuário
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o usuário não existir
     */
    @Override
    public AnimeBindingSummaryPage listUserWatchlist(Integer userId, Integer page, Integer size) {
        return userService.listUserWatchlist(userId, page, size);
    }

    /**
     * {@code POST /v1/users/{userId}/watchlist} — adiciona um título à lista do usuário.
     *
     * @param userId id do usuário
     * @param watchlistItemRequest anime a adicionar
     * @throws ResourceNotFoundException (traduzida em 404) se o usuário ou o anime não existir
     */
    @Override
    public void addUserWatchlistItem(Integer userId, WatchlistItemRequest watchlistItemRequest) {
        userService.addUserWatchlistItem(userId, watchlistItemRequest);
    }

    /**
     * {@code DELETE /v1/users/{userId}/watchlist/{anilistId}} — remove um título da lista do
     * usuário.
     *
     * @param userId id do usuário
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException (traduzida em 404) se o usuário não existir
     */
    @Override
    public void removeUserWatchlistItem(Integer userId, Integer anilistId) {
        userService.removeUserWatchlistItem(userId, anilistId);
    }

}
