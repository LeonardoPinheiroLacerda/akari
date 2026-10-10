package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Linha singleton (sempre {@code id = SINGLETON_ID}) com a release atualmente ingerida do
 * dataset do anibridge. {@code upstreamUpdatedAt} é a chave de comparação usada pelo refresh
 * pra decidir se vale re-baixar — {@code releaseTag}/{@code publishedAt} ficam congelados no
 * upstream e não refletem os refreshes diários.
 */
@Entity
@Table(name = "anibridge_mapping_release")
public class MappingRelease extends PanacheEntityBase {

    public static final Integer SINGLETON_ID = 1;

    @Id
    public Integer id;

    @Column(name = "release_tag")
    public String releaseTag;

    @Column(name = "published_at")
    public OffsetDateTime publishedAt;

    @Column(name = "upstream_updated_at")
    public OffsetDateTime upstreamUpdatedAt;

    @Column(name = "ingested_at")
    public OffsetDateTime ingestedAt;

    @Column(name = "segment_count")
    public Integer segmentCount;

    /**
     * Busca a linha singleton existente, ou devolve uma nova instância não persistida (ainda
     * sem {@code id} setado) caso seja a primeira ingestão.
     *
     * @return a release atual, ou uma nova instância pronta pra ser preenchida e persistida
     */
    public static MappingRelease getOrCreate() {
        final MappingRelease existing = MappingRelease.findById(SINGLETON_ID);
        if (existing != null) {
            return existing;
        }

        final MappingRelease mappingRelease = new MappingRelease();
        mappingRelease.id = SINGLETON_ID;
        return mappingRelease;
    }

}