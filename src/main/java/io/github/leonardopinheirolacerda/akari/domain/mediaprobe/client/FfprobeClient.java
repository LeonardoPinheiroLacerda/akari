package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeOutput;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invoca o binário {@code ffprobe} sobre um arquivo de vídeo e parseia a saída JSON.
 *
 * <p>A saída do processo é redirecionada pra um arquivo temporário em vez de lida do pipe de
 * stdout: mídia com muitas faixas gera JSON maior que o buffer do pipe (~64KB), e ler o pipe só
 * depois do {@code waitFor()} travaria — o processo bloqueia escrevendo, ninguém drena, o
 * timeout estoura. Com arquivo, {@code waitFor()} funciona para qualquer tamanho de saída.
 *
 * <p>Não traduz falhas em {@code AkariException} — lança {@link FfprobeException}, que quem
 * chama (o {@code MediaProbeService}) embrulha em {@code IntegrationException}.
 */
@ApplicationScoped
public class FfprobeClient {

    @ConfigProperty(name = "akari.mediaprobe.ffprobe.binary", defaultValue = "ffprobe")
    String ffprobeBinary;

    @ConfigProperty(name = "akari.mediaprobe.ffprobe.timeout", defaultValue = "PT30S")
    Duration timeout;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Roda {@code ffprobe -show_format -show_streams} sobre o arquivo e parseia a saída.
     *
     * <p>Assume que o arquivo existe — a checagem de existência é responsabilidade de quem
     * chama.
     *
     * @param file arquivo de vídeo já resolvido em disco
     * @return metadados crus do container e dos streams, como o ffprobe os emitiu
     * @throws FfprobeException se o binário não puder ser iniciado, o processo não responder
     *         dentro do timeout, terminar com código de saída não-zero, ou a saída não for um
     *         JSON válido
     */
    public FfprobeOutput probe(Path file) {
        final Path outputFile = createOutputFile();

        try {
            return runProbe(file, outputFile);
        } finally {
            deleteQuietly(outputFile);
        }
    }

    private Path createOutputFile() {
        try {
            return Files.createTempFile("ffprobe-", ".json");
        } catch (IOException e) {
            throw new FfprobeException("Não foi possível criar o arquivo temporário para a saída do ffprobe", e);
        }
    }

    private FfprobeOutput runProbe(Path file, Path outputFile) {
        final List<String> command = List.of(
                ffprobeBinary,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                file.toString()
        );

        final ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.DISCARD);

        final Process process = start(processBuilder);

        awaitCompletion(process);

        if (process.exitValue() != 0) {
            throw new FfprobeException("ffprobe terminou com código de saída " + process.exitValue());
        }

        try {
            return objectMapper.readValue(outputFile.toFile(), FfprobeOutput.class);
        } catch (IOException e) {
            throw new FfprobeException("Saída do ffprobe não é um JSON válido", e);
        }
    }

    private Process start(ProcessBuilder processBuilder) {
        try {
            return processBuilder.start();
        } catch (IOException e) {
            throw new FfprobeException(
                    "Não foi possível iniciar o ffprobe (binário '" + ffprobeBinary + "' ausente ou sem permissão de execução)",
                    e
            );
        }
    }

    private void awaitCompletion(Process process) {
        final boolean finished;

        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfprobeException("Interrompido aguardando o ffprobe retornar", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new FfprobeException("ffprobe não respondeu em " + timeout);
        }
    }

    private void deleteQuietly(Path outputFile) {
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException e) {
            Log.warnf(e, "Falha ao apagar o arquivo temporário de saída do ffprobe %s", outputFile);
        }
    }

}
