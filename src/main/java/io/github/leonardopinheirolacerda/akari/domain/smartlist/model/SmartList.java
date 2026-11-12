package io.github.leonardopinheirolacerda.akari.domain.smartlist.model;

import io.github.leonardopinheirolacerda.akari.api.dto.SmartListSortField;
import io.github.leonardopinheirolacerda.akari.api.dto.SortDirection;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lista exibida no front, definida por um filtro fixo e uma ordenação — os itens não são
 * gravados, são recalculados a cada consulta.
 */
@Entity
@Table(name = "smart_list")
public class SmartList extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    public String name;

    /**
     * Posição na listagem das listas; menor aparece primeiro.
     */
    public Integer position;

    @JdbcTypeCode(SqlTypes.JSON)
    public SmartListCriteria criteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_field")
    public SmartListSortField sortField;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_direction")
    public SortDirection sortDirection;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Busca pelo id.
     *
     * @param id id da lista
     * @return a lista, se existir
     */
    public static Optional<SmartList> find(Integer id) {
        return SmartList
                .find("id", id)
                .firstResultOptional();
    }

    /**
     * Todas as listas, na ordem de exibição.
     *
     * @return as listas, por {@code position} e depois {@code id}
     */
    public static List<SmartList> listAllOrdered() {
        return SmartList.listAll(Sort.by("position").and("id"));
    }

}
