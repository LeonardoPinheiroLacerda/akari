package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneObscureRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneObscureResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.annotation.Obscure;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Obscurece os campos sensíveis de uma config bruta de storage provider antes de persistir, e
 * valida o resultado contra o shape esperado do provider.
 */
@ApplicationScoped
public class ObscureFieldApplier {

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    Validator validator;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Obscurece, via {@code /core/obscure} do rclone, todo campo de {@code configType} anotado
     * com {@link Obscure} que vier presente e não-branco em {@code config}, e então valida o
     * config inteiro (Bean Validation) contra o shape de {@code configType}.
     *
     * @param config config bruta, como recebida da API (campos sensíveis ainda em claro)
     * @param configType tipo esperado da config pro {@code type} do provider (ex.:
     *                   {@code MegaConfig}, {@code LocalConfig}), usado tanto pra achar os
     *                   campos {@link Obscure} via reflection quanto pra validar o shape
     * @param <T> tipo da config do provider
     * @return o mesmo mapa recebido, com os campos sensíveis já obscurecidos
     * @throws ConstraintViolationException se o config, convertido pro {@code configType}, não
     *         passar nas constraints de Bean Validation (ex.: campo obrigatório ausente)
     * @throws ProcessingException se a chamada ao rclone pra obscurecer falhar no transporte
     * @throws WebApplicationException se o rclone responder com status de erro
     */
    public <T> Map<String, String> obscureRequiredFields(Map<String, String> config, Class<T> configType) {
        for (Field field : configType.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Obscure.class)) {
                continue;
            }

            final String fieldName = field.getName();
            final String clear = config.get(fieldName);

            if (clear != null && !clear.isBlank()) {
                try {
                    final RcloneObscureRequest request = new RcloneObscureRequest(clear);
                    final RcloneObscureResponse obscure = rcloneClient.obscure(request);
                    config.put(fieldName, obscure.obscured());
                    Log.infof("Campo \"%s\" obscurecido com sucesso", fieldName);
                } catch (ProcessingException | WebApplicationException e) {
                    Log.errorf(e, "Falha ao obscurecer o campo \"%s\" via rclone", fieldName);
                    throw e;
                }
            }
        }

        final T object = objectMapper.convertValue(config, configType);

        final Set<ConstraintViolation<T>> violations = validator.validate(object);

        if (!violations.isEmpty()) {
            Log.warnf("Config inválida pro tipo %s: %d violação(ões)", configType.getSimpleName(), violations.size());
            throw new ConstraintViolationException(violations);
        }

        return config;
    }

}
