package io.github.leonardopinheirolacerda.akari.domain.playback.parser;

import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê quanto de mídia uma playlist HLS já tem transcodificado, somando as durações declaradas
 * nela — usado pelo {@code seek} pra decidir se o instante pedido já está coberto sem precisar
 * reiniciar o transcode.
 */
public final class HlsPlaylistParser {

    private static final Pattern EXTINF_PATTERN = Pattern.compile("#EXTINF:([\\d.]+),");

    private HlsPlaylistParser() {
    }

    /**
     * Soma as durações ({@code #EXTINF}) de todos os segmentos já escritos na playlist.
     *
     * @param playlistFile arquivo {@code .m3u8} da sessão
     * @return a soma das durações, em segundos; {@code 0.0} se o arquivo ainda não existir (uma
     *         sessão nova sem segmento algum não é erro, só não tem nada transcodificado ainda)
     */
    public static double sumExtinfSeconds(Path playlistFile) {
        if (!Files.isRegularFile(playlistFile)) {
            return 0.0;
        }

        final String content;

        try {
            content = Files.readString(playlistFile);
        } catch (IOException e) {
            Log.warnf(e, "Falha ao ler a playlist %s pra somar a duração transcodificada", playlistFile);
            return 0.0;
        }

        final Matcher matcher = EXTINF_PATTERN.matcher(content);
        double total = 0.0;

        while (matcher.find()) {
            total += Double.parseDouble(matcher.group(1));
        }

        return total;
    }

}
