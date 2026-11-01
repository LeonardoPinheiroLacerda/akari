package io.github.leonardopinheirolacerda.akari.domain.playback.registry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Estado observado de um processo {@code ffmpeg} rastreado pelo {@link TranscodeProcessRegistry}:
 * a referência ao {@link Process} (que guarda o código de saída depois que ele termina), se o
 * encerramento foi pedido pelo backend e as últimas linhas do stderr, capturadas enquanto o
 * processo roda — o diretório de trabalho é apagado na limpeza, então não haveria como lê-las
 * depois.
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
     * Diz se o processo já terminou com falha: código de saída diferente de {@code 0} — no Unix,
     * morte por sinal também aparece assim ({@code 128 + sinal}). Sair com {@code 0} é conclusão
     * normal (um remux termina em segundos), não falha.
     *
     * <p>Lê o código do próprio {@link Process} em vez de uma cópia gravada no {@code onExit}:
     * o callback roda assíncrono e pode chegar depois de quem consulta já ver o processo morto.
     *
     * @return {@code true} se o processo já terminou e o código de saída não é {@code 0};
     *         {@code false} se ainda está rodando ou se terminou com sucesso
     */
    boolean exitedWithFailure() {
        return !process.isAlive() && process.exitValue() != 0;
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
