package io.github.leonardopinheirolacerda.akari.domain.playback.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Limpeza do diretório de trabalho de uma sessão de playback (o {@code <base-dir>/<sessionId>}
 * com o m3u8 e os segmentos). A criação do diretório não é responsabilidade desta classe — fica
 * junto da subida do ffmpeg, no {@link PlaybackService}.
 */
@ApplicationScoped
public class PlaybackWorkspaceService {

    /**
     * Apaga o diretório da sessão por completo (arquivos e a própria pasta). Idempotente:
     * diretório ausente é no-op. Best-effort — falhas de IO são registradas, não propagadas, pra
     * não travar a limpeza das demais sessões.
     *
     * @param workDir diretório de trabalho da sessão a apagar
     */
    public void delete(Path workDir) {
        if (!Files.isDirectory(workDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            Log.warnf(e, "Falha ao limpar o diretório de trabalho %s", workDir);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Log.warnf(e, "Falha ao apagar %s", path);
        }
    }

}
