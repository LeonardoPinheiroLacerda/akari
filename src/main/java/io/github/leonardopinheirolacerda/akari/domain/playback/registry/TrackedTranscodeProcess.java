package io.github.leonardopinheirolacerda.akari.domain.playback.registry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Estado observado de um processo {@code ffmpeg} rastreado pelo {@link TranscodeProcessRegistry}:
 * a referência ao {@link Process}, se o encerramento foi pedido pelo backend e as últimas linhas
 * do stderr, capturadas enquanto o processo roda — o diretório de trabalho é apagado na limpeza,
 * então não haveria como lê-las depois.
 */
class TrackedTranscodeProcess {

    private static final int STDERR_TAIL_LINES = 50;

    final Process process;

    private final Deque<String> stderrTail = new ArrayDeque<>();

    volatile boolean stoppedByBackend = false;

    volatile boolean missingPlaylistLogged = false;

    TrackedTranscodeProcess(Process process) {
        this.process = process;
    }

    /**
     * Guarda mais uma linha do stderr, descartando a mais antiga quando passa de
     * {@value #STDERR_TAIL_LINES} linhas.
     *
     * @param line linha lida do stderr do processo
     */
    synchronized void appendStderrLine(String line) {
        stderrTail.addLast(line);

        if (stderrTail.size() > STDERR_TAIL_LINES) {
            stderrTail.removeFirst();
        }
    }

    /**
     * @return as últimas linhas do stderr capturadas até agora, da mais antiga para a mais nova
     */
    synchronized List<String> stderrTailSnapshot() {
        return List.copyOf(stderrTail);
    }

}
