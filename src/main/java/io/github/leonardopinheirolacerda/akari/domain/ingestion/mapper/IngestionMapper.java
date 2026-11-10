package io.github.leonardopinheirolacerda.akari.domain.ingestion.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.IngestionPage;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStepEventResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStepExecutionDetail;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionTaskDetail;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionTaskStepStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionTaskSummary;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.Ingestion;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepEvent;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepExecution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte os models da ingestão nos DTOs do contrato. Os campos calculados — contagens,
 * tarefas, título e poster do anime resolvidos, status dos steps e eventos — ficam ignorados
 * aqui e são preenchidos pelo {@code IngestionService}.
 */
@Mapper(componentModel = "cdi")
public interface IngestionMapper {

    @Mapping(target = "taskCounts", ignore = true)
    @Mapping(target = "currentTask", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    IngestionSummary toSummary(Ingestion ingestion);

    IngestionPage toPage(PageResult<IngestionSummary> pageResult);

    @Mapping(target = "ingestionId", source = "ingestion.id")
    @Mapping(target = "folderId", source = "folder.id")
    @Mapping(target = "folderName", source = "folder.name")
    @Mapping(target = "anilistId", source = "anime.anilistId")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "thumbnail", ignore = true)
    @Mapping(target = "steps", ignore = true)
    IngestionTaskSummary toTaskSummary(IngestionTask task);

    @Mapping(target = "ingestionId", source = "ingestion.id")
    @Mapping(target = "folderId", source = "folder.id")
    @Mapping(target = "folderName", source = "folder.name")
    @Mapping(target = "anilistId", source = "anime.anilistId")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "thumbnail", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "executions", ignore = true)
    IngestionTaskDetail toTaskDetail(IngestionTask task);

    IngestionTaskStepStatus toTaskStepStatus(IngestionStepExecution execution);

    @Mapping(target = "events", ignore = true)
    IngestionStepExecutionDetail toExecutionDetail(IngestionStepExecution execution);

    @Mapping(target = "type", source = "eventType")
    IngestionStepEventResponse toEventResponse(IngestionStepEvent event);

}
