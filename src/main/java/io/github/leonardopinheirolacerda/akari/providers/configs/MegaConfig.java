package io.github.leonardopinheirolacerda.akari.providers.configs;

import io.github.leonardopinheirolacerda.akari.providers.annotations.Obscure;
import jakarta.validation.constraints.NotBlank;

/**
 * Schema da configuração exigida por um provider do tipo MEGA.
 *
 * @param email endereço de e-mail da conta MEGA; obrigatório e não pode estar em branco
 * @param password senha da conta MEGA; obrigatória e não pode estar em branco
 */
public record MegaConfig(

        @NotBlank(message = "O email é obrigatório para o provider MEGA")
        String email,

        @Obscure
        @NotBlank(message = "A senha é obrigatória para o provider MEGA")
        String password
) {
}
