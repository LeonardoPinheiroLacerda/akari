package io.github.leonardopinheirolacerda.akari.domain.torrent.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Release de torrent enviada ao qBittorrent, com o andamento do download.
 */
@Entity
@Table(name = "torrent_release")
public class TorrentRelease extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TorrentProviderType provider;

    @Column(name = "info_hash", nullable = false, unique = true)
    public String infoHash;

    @Column(name = "magnet_uri", nullable = false)
    public String magnetUri;

    @Column(nullable = false)
    public String title;

    public Integer episode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anilist_id")
    public Anime anime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TorrentDownloadStatus status;

    @Column(nullable = false)
    public Double progress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "completed_at")
    public OffsetDateTime completedAt;

    /**
     * Busca uma release pelo info-hash.
     *
     * @param infoHash info-hash BitTorrent, em minúsculas
     * @return a release, se já foi enviada ao qBittorrent alguma vez
     */
    public static Optional<TorrentRelease> findByInfoHash(String infoHash) {
        return TorrentRelease
                .find("infoHash", infoHash)
                .firstResultOptional();
    }

    /**
     * Lista as releases em download, já com o anime, a pasta dele e o storage provider da pasta
     * carregados (o sync roda fora de transação).
     *
     * @return as releases com status {@code DOWNLOADING}
     */
    public static List<TorrentRelease> listDownloading() {
        return TorrentRelease.list(
                "from TorrentRelease release"
                        + " join fetch release.anime anime"
                        + " join fetch anime.folder folder"
                        + " join fetch folder.storageProvider"
                        + " where release.status = ?1",
                TorrentDownloadStatus.DOWNLOADING
        );
    }

    /**
     * Pagina as releases já enviadas ao qBittorrent, da mais recente para a mais antiga.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de releases em download ou concluídas
     */
    public static PageResult<TorrentRelease> findDownloadsPage(Integer page, Integer size) {
        final PanacheQuery<TorrentRelease> panacheQuery = TorrentRelease
                .find(
                        "status in ?1 order by createdAt desc, id desc",
                        List.of(TorrentDownloadStatus.DOWNLOADING, TorrentDownloadStatus.READY)
                )
                .page(page, size);

        return new PageResult<>(
                panacheQuery.list(),
                page,
                size,
                panacheQuery.count(),
                panacheQuery.pageCount()
        );
    }
}
