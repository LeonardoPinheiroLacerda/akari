package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.parser;

import java.util.Map;

/**
 * Conversões dos valores crus que o {@code ffprobe} emite (números como string, fração de frame
 * rate, tags e flags de disposition) pros tipos usados pelo {@code MediaProbeMapper}.
 *
 * <p>Fica fora do mapper porque o MapStruct trataria um {@code default} method com assinatura
 * igual a outra conversão do mesmo mapper (ex.: {@code String→Double} usado tanto por
 * {@code frameRate} quanto por {@code durationSeconds}) como candidato ambíguo se não fosse
 * qualificado — mantendo a lógica aqui, o mapper só referencia os métodos certos por nome
 * (ver os métodos {@code @Named} de {@code MediaProbeMapper}, que delegam pra cá).
 */
public final class FfprobeValueParser {

    private FfprobeValueParser() {
    }

    /**
     * Converte uma string numérica do ffprobe em {@code Double}.
     *
     * @param value valor cru; {@code null} ou em branco devolve {@code null}
     * @return o valor convertido, ou {@code null} se ausente ou não-numérico
     */
    public static Double toDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converte uma string numérica do ffprobe em {@code Long}.
     *
     * @param value valor cru; {@code null} ou em branco devolve {@code null}
     * @return o valor convertido, ou {@code null} se ausente ou não-numérico
     */
    public static Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converte uma string numérica do ffprobe em {@code Integer}.
     *
     * @param value valor cru; {@code null} ou em branco devolve {@code null}
     * @return o valor convertido, ou {@code null} se ausente ou não-numérico
     */
    public static Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calcula os quadros por segundo a partir da fração {@code avg_frame_rate} do ffprobe.
     *
     * @param fraction fração no formato {@code "numerador/denominador"} (ex.
     *                 {@code "24000/1001"}); {@code null} devolve {@code null}
     * @return o valor calculado; {@code null} se a fração for {@code "0/0"}, malformada, ou o
     *         denominador for zero
     */
    public static Double frameRate(String fraction) {
        if (fraction == null || fraction.isBlank()) {
            return null;
        }

        final String[] parts = fraction.split("/", 2);
        if (parts.length != 2) {
            return null;
        }

        final Double numerator = toDouble(parts[0]);
        final Double denominator = toDouble(parts[1]);

        if (numerator == null || denominator == null || denominator == 0.0) {
            return null;
        }

        return numerator / denominator;
    }

    /**
     * Lê a tag {@code language} de um stream.
     *
     * @param tags mapa de tags do stream; {@code null} devolve {@code null}
     * @return o idioma declarado (código ISO 639, ex. {@code "jpn"}), ou {@code null} se ausente
     */
    public static String language(Map<String, String> tags) {
        return tags == null ? null : tags.get("language");
    }

    /**
     * Lê a tag {@code title} de um stream.
     *
     * @param tags mapa de tags do stream; {@code null} devolve {@code null}
     * @return o título declarado, ou {@code null} se ausente
     */
    public static String title(Map<String, String> tags) {
        return tags == null ? null : tags.get("title");
    }

    /**
     * Lê a flag {@code default} do mapa de disposition de um stream.
     *
     * @param disposition mapa de disposition do stream; {@code null} devolve {@code false}
     * @return {@code true} se a flag estiver presente com valor {@code 1}
     */
    public static Boolean defaultFlag(Map<String, Integer> disposition) {
        return flag(disposition, "default");
    }

    /**
     * Lê a flag {@code forced} do mapa de disposition de um stream.
     *
     * @param disposition mapa de disposition do stream; {@code null} devolve {@code false}
     * @return {@code true} se a flag estiver presente com valor {@code 1}
     */
    public static Boolean forcedFlag(Map<String, Integer> disposition) {
        return flag(disposition, "forced");
    }

    /**
     * Lê a flag {@code hearing_impaired} do mapa de disposition de um stream.
     *
     * @param disposition mapa de disposition do stream; {@code null} devolve {@code false}
     * @return {@code true} se a flag estiver presente com valor {@code 1}
     */
    public static Boolean hearingImpairedFlag(Map<String, Integer> disposition) {
        return flag(disposition, "hearing_impaired");
    }

    private static Boolean flag(Map<String, Integer> disposition, String key) {
        if (disposition == null) {
            return false;
        }
        return Integer.valueOf(1).equals(disposition.get(key));
    }

}
