package io.github.leonardopinheirolacerda.akari.domain.playback.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Encerra processos do SO por pid, com confirmação de posse — não sabe nada sobre ffmpeg/HLS,
 * é gerência de processo genérica, separada do {@code FfmpegClient} (que só sabe montar o
 * comando e subir o processo).
 *
 * <p>Nunca lança — um processo que não morre não deve travar a substituição ou a varredura da
 * sessão que o chamou, vira só um aviso no log.
 */
@ApplicationScoped
public class ProcessTerminationService {

    private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofSeconds(5);
    private static final Duration PID_REUSE_TOLERANCE = Duration.ofSeconds(10);

    /**
     * Encerra um processo, se ele ainda for o dono do pid informado.
     *
     * <p>Confere a posse comparando o instante de início real do processo vivo com
     * {@code processStartedAt} (tolerância de {@value #PID_REUSE_TOLERANCE}) antes de agir —
     * evita matar um processo não relacionado que o SO reatribuiu ao mesmo pid. Tenta SIGTERM
     * primeiro, esperando até {@value #TERMINATION_GRACE_PERIOD}; força SIGKILL se não sair a
     * tempo.
     *
     * @param pid pid do processo a encerrar; {@code null} é um no-op
     * @param processStartedAt instante em que o processo esperado começou, pra confirmar posse
     */
    public void terminate(Long pid, OffsetDateTime processStartedAt) {
        if (pid == null) {
            return;
        }

        final Optional<ProcessHandle> handle = ProcessHandle.of(pid);

        if (handle.isEmpty()) {
            Log.infof("Processo %d já não existe", pid);
            return;
        }

        if (!ownsProcess(handle.get(), processStartedAt)) {
            Log.warnf("Pid %d não é mais o processo esperado (provável reaproveitamento pelo SO) — ignorando", pid);
            return;
        }

        terminateOwnedProcess(handle.get(), pid);
    }

    private boolean ownsProcess(ProcessHandle handle, OffsetDateTime processStartedAt) {
        if (processStartedAt == null) {
            return false;
        }

        final Optional<Instant> actualStart = handle.info().startInstant();

        if (actualStart.isEmpty()) {
            return false;
        }

        final Duration difference = Duration.between(processStartedAt.toInstant(), actualStart.get()).abs();

        return difference.compareTo(PID_REUSE_TOLERANCE) <= 0;
    }

    private void terminateOwnedProcess(ProcessHandle handle, Long pid) {
        handle.destroy();

        try {
            handle.onExit()
                  .get(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            Log.warnf("Processo %d não terminou a tempo, forçando encerramento", pid);
            handle.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warnf(e, "Interrompido aguardando o processo %d terminar", pid);
        } catch (ExecutionException e) {
            Log.warnf(e, "Falha aguardando o processo %d terminar", pid);
        }
    }

}
