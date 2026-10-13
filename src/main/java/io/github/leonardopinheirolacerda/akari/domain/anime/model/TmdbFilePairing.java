package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;

/**
 * Pairing TMDB (temporada/episódio/thumbnail) de um arquivo de vídeo. A chave primária é o
 * próprio {@code fileId} — um arquivo tem no máximo um pairing.
 */
@Entity
@Table(name = "tmdb_file_pairing")
public class TmdbFilePairing extends PanacheEntityBase {

    @Id
    @Column(name = "file_id")
    public Integer fileId;

    @Column(name = "season_number")
    public Integer seasonNumber;

    @Column(name = "episode_number")
    public Integer episodeNumber;

    @Column(name = "thumbnail_path")
    public String thumbnailPath;

    @Column(name = "episode_name")
    public String episodeName;

    @Column(name = "episode_overview")
    public String episodeOverview;

    @Column(name = "episode_air_date")
    public String episodeAirDate;

    @Column(name = "episode_runtime")
    public Integer episodeRuntime;

    @Column(name = "episode_vote_average")
    public Double episodeVoteAverage;

    @Column(name = "episode_vote_count")
    public Integer episodeVoteCount;

    @Column(name = "episode_type")
    public String episodeType;

    /**
     * Busca pelo {@code fileId}, a própria chave primária.
     *
     * @param fileId id do arquivo no catalog
     * @return o pairing, se existir
     */
    public static Optional<TmdbFilePairing> find(Integer fileId) {
        return TmdbFilePairing
                .find("fileId", fileId)
                .firstResultOptional();
    }

    /**
     * Busca os pairings de qualquer um dos arquivos informados.
     *
     * @param fileIds ids dos arquivos no catalog
     * @return os pairings existentes; arquivos sem pairing simplesmente não aparecem
     */
    public static List<TmdbFilePairing> findByFileIds(List<Integer> fileIds) {
        return TmdbFilePairing
                .find("fileId in ?1", fileIds)
                .list();
    }

}
