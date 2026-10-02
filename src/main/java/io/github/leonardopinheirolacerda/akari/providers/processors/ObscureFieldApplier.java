package io.github.leonardopinheirolacerda.akari.providers.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.clients.rclone.RcloneClient;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneObscureRequest;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneObscureResponse;
import io.github.leonardopinheirolacerda.akari.providers.annotations.Obscure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ObscureFieldApplier {

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    Validator validator;

    @Inject
    ObjectMapper objectMapper;

    public <T> Map<String, String> obscureRequiredFields(Map<String, String> config, Class<T> configType) {
        for (Field field : configType.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Obscure.class)) {
                continue;
            }

            final String fieldName = field.getName();
            final String clear = config.get(fieldName);

            if (clear != null && !clear.isBlank()) {
                final RcloneObscureResponse obscure = rcloneClient.obscure(new RcloneObscureRequest(clear));
                config.put(fieldName, obscure.obscured());
            }
        }

        final T object = objectMapper.convertValue(config, configType);

        final Set<ConstraintViolation<T>> violations = validator.validate(object);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return config;
    }

}