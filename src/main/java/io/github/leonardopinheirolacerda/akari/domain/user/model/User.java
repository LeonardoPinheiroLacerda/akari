package io.github.leonardopinheirolacerda.akari.domain.user.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Perfil de usuário, sem senha nem autenticação — só identifica quem está assistindo, pra
 * separar o histórico de visualização e a lista de títulos de cada um.
 */
@Entity
@Table(name = "app_user")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    public String name;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Busca pelo id.
     *
     * @param id id do usuário
     * @return o usuário, se existir
     */
    public static Optional<User> find(Integer id) {
        return User
                .find("id", id)
                .firstResultOptional();
    }

    /**
     * Todos os usuários, por {@code id}.
     *
     * @return os usuários
     */
    public static List<User> listAllOrdered() {
        return User.listAll(Sort.by("id"));
    }

}
