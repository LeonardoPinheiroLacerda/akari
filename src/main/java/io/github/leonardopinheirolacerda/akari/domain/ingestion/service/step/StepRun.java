package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepExecution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionEventType;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;

import java.util.Map;

/**
 * Uma execução aberta de um step — devolvida pelo {@link IngestionStepService#start}, guarda a
 * {@link IngestionStepExecution} pra que o step grave os eventos e feche a execução sem
 * repassá-la a cada chamada. Um objeto por execução, não um campo do service, porque o service
 * é {@code @ApplicationScoped}: um campo seria compartilhado entre as execuções. Grava pelos
 * métodos {@code @Transactional} do próprio step service, então cada evento continua numa
 * transação curta.
 *
 * <p>Um método por {@link IngestionEventType}, cada um com e sem {@code details}: a
 * {@code message} é a descrição legível do evento e {@code details}, os dados estruturados
 * gravados em {@code JSONB}. Tipo novo no enum precisa do par de métodos aqui.
 */
public final class StepRun {

    private final IngestionStepService service;

    private final IngestionStepExecution execution;

    StepRun(IngestionStepService service, IngestionStepExecution execution) {
        this.service = service;
        this.execution = execution;
    }

    /** Grava um evento {@code STARTED}, sem dados estruturados. */
    public void started(String message) {
        started(message, null);
    }

    /** Grava um evento {@code STARTED} com dados estruturados. */
    public void started(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.STARTED, message, details);
    }

    /** Grava um evento {@code REQUEST_SENT}, sem dados estruturados. */
    public void requestSent(String message) {
        requestSent(message, null);
    }

    /** Grava um evento {@code REQUEST_SENT} com dados estruturados. */
    public void requestSent(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.REQUEST_SENT, message, details);
    }

    /** Grava um evento {@code RESPONSE_RECEIVED}, sem dados estruturados. */
    public void responseReceived(String message) {
        responseReceived(message, null);
    }

    /** Grava um evento {@code RESPONSE_RECEIVED} com dados estruturados. */
    public void responseReceived(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.RESPONSE_RECEIVED, message, details);
    }

    /** Grava um evento {@code RESULTS_FOUND}, sem dados estruturados. */
    public void resultsFound(String message) {
        resultsFound(message, null);
    }

    /** Grava um evento {@code RESULTS_FOUND} com dados estruturados. */
    public void resultsFound(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.RESULTS_FOUND, message, details);
    }

    /** Grava um evento {@code MATCH_SELECTED}, sem dados estruturados. */
    public void matchSelected(String message) {
        matchSelected(message, null);
    }

    /** Grava um evento {@code MATCH_SELECTED} com dados estruturados. */
    public void matchSelected(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.MATCH_SELECTED, message, details);
    }

    /** Grava um evento {@code MATCH_NOT_FOUND}, sem dados estruturados. */
    public void matchNotFound(String message) {
        matchNotFound(message, null);
    }

    /** Grava um evento {@code MATCH_NOT_FOUND} com dados estruturados. */
    public void matchNotFound(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.MATCH_NOT_FOUND, message, details);
    }

    /** Grava um evento {@code WARNING}, sem dados estruturados. */
    public void warning(String message) {
        warning(message, null);
    }

    /** Grava um evento {@code WARNING} com dados estruturados. */
    public void warning(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.WARNING, message, details);
    }

    /** Grava um evento {@code ERROR}, sem dados estruturados. */
    public void error(String message) {
        error(message, null);
    }

    /** Grava um evento {@code ERROR} com dados estruturados. */
    public void error(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.ERROR, message, details);
    }

    /** Grava um evento {@code COMPLETED}, sem dados estruturados. */
    public void completed(String message) {
        completed(message, null);
    }

    /** Grava um evento {@code COMPLETED} com dados estruturados. */
    public void completed(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.COMPLETED, message, details);
    }

    /** Grava um evento {@code SKIPPED}, sem dados estruturados. */
    public void skipped(String message) {
        skipped(message, null);
    }

    /** Grava um evento {@code SKIPPED} com dados estruturados. */
    public void skipped(String message, Map<String, Object> details) {
        service.record(execution, IngestionEventType.SKIPPED, message, details);
    }

    /**
     * Fecha a execução com o status final do step.
     *
     * @param status status final: {@code COMPLETED}, {@code SKIPPED}, {@code UNRESOLVED} ou
     *               {@code FAILED}
     */
    public void finish(IngestionStepStatus status) {
        service.finish(execution, status);
    }
}
