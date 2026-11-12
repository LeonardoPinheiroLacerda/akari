package io.github.leonardopinheirolacerda.akari.domain.smartlist.model;

import java.util.List;

/**
 * Filtro de uma {@link SmartList}, gravado como JSON na coluna {@code criteria}. Campo
 * {@code null} não filtra.
 *
 * <p>Os critérios de franquia (gêneros, tags, estúdios, status, adulto) valem pra qualquer membro
 * da franquia; os demais valem só pra raiz.
 */
public class SmartListCriteria {

    public List<String> includeGenres;

    public List<String> excludeGenres;

    public List<String> includeTags;

    public List<String> excludeTags;

    public List<String> studios;

    public List<String> statuses;

    public List<String> formats;

    public Integer yearFrom;

    public Integer yearTo;

    public Double minScore;

    public Double minMeanScore;

    public Integer minPopularity;

    public Boolean adult;

}
