package io.github.leonardopinheirolacerda.akari.domain.setting.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Preferência administrável do sistema, persistida na tabela {@code setting}.
 *
 * <p>Chave/valor genérico: o {@code value} é sempre texto bruto, tipado só no ponto de leitura
 * (ver os getters tipados em {@code SettingService}, ex. {@code getCacheDir}/
 * {@code getTmdbApiKey}). {@code secret} marca preferências cujo valor não deve ser exibido em
 * claro pela API (mascarado na resposta).
 */
@Entity
@Table(name = "setting")
public class Setting extends PanacheEntityBase {

    /** Identificador da preferência (ex. {@code "cache.dir"}, {@code "tmdb.api-key"}). */
    @Id
    public String key;

    /** Valor bruto da preferência; {@code null} ou em branco quando ainda não configurada. */
    public String value;

    /** Se {@code true}, a API mascara o valor em respostas de leitura. */
    public Boolean secret;

    /** Atualizado automaticamente pelo Hibernate a cada escrita no {@code value}. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    /**
     * Confirma se a preferência tem um valor utilizável.
     *
     * @return {@code true} se {@code value} não é nulo nem em branco
     */
    public boolean isConfigured() {
        return value != null && !value.isBlank();
    }

    /**
     * Busca uma preferência pela chave exata.
     *
     * @param key chave da preferência
     * @return a preferência; vazio se não existir
     */
    public static Optional<Setting> find(String key) {
        return Setting
                .find("key = ?1", key)
                .firstResultOptional();
    }

    /**
     * Lista todas as preferências cadastradas.
     *
     * @return todas as preferências, ordenadas por chave
     */
    public static List<Setting> listAll() {
        return Setting.listAll(Sort.by("key"));
    }

    /**
     * Busca preferências cuja chave contém o trecho informado.
     *
     * @param name trecho a buscar na chave, case-insensitive
     * @return as preferências cuja chave contém {@code name}, ordenadas por chave
     */
    public static List<Setting> findByName(String name) {
        return Setting
                .find("lower(key) like lower(?1)", Sort.by("key"), "%" + name + "%")
                .list();
    }

}
