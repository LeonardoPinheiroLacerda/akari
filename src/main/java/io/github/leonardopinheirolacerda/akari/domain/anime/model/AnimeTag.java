package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Tag da AniList atribuída a um anime, gravada em {@code anime_tag} — muitas por anime.
 */
@Embeddable
public class AnimeTag {

    public String name;

    /**
     * Quanto a tag se aplica ao anime, de 0 a 100.
     */
    public Integer rank;

    /**
     * {@code true} se a tag revela parte da história.
     */
    @Column(name = "is_spoiler")
    public Boolean spoiler;

}
