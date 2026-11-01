package io.github.leonardopinheirolacerda.akari.domain.playback.parser;

import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o conteúdo das playlists HLS geradas pelo ffmpeg: quanto de mídia uma playlist já tem
 * transcodificado, somando as durações declaradas nela — usado pelo {@code seek} pra decidir se o
 * instante pedido já está coberto sem precisar reiniciar o transcode — e se um master playlist
 * já lista alguma variante.
 */
public final class HlsPlaylistParser {

    private static final Pattern EXTINF_PATTERN = Pattern.compile("#EXTINF:([\\d.]+),");

    private static final String STREAM_INF_TAG = "#EXT-X-STREAM-INF";

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

    /**
     * {@code true} se o master playlist já lista ao menos uma variante: uma linha
     * {@code #EXT-X-STREAM-INF} seguida da URI da variant playlist.
     *
     * <p>O ffmpeg escreve o master uma vez durante o transcode, com o {@code BANDWIDTH} tirado do
     * {@code bit_rate} dos streams; se nenhum stream tiver bitrate (vídeo copiado sem o
     * {@code -b:v} do {@code FfmpegClient}, que só sai quando o probe informa bitrate), grava um
     * arquivo completo, mas só com o {@code #EXT-X-MEDIA} da legenda, e só reescreve com a
     * variante no fim do transcode, com a banda medida. Esse master não é uma playlist que o
     * player consiga usar.
     *
     * @param content conteúdo do master playlist
     * @return {@code true} se houver ao menos um {@code #EXT-X-STREAM-INF} com URI
     */
    public static boolean hasVariantStream(String content) {
        final List<String> lines = content.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();

        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).startsWith(STREAM_INF_TAG) && !lines.get(i + 1).startsWith("#")) {
                return true;
            }
        }

        return false;
    }

}
