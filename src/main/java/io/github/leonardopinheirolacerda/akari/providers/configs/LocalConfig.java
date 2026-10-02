package io.github.leonardopinheirolacerda.akari.providers.configs;

import jakarta.validation.constraints.NotBlank;

/**
 * Schema da configuração exigida por um provider do tipo LOCAL.
 *
 * @param rootPath caminho absoluto do diretório raiz dos arquivos no disco do servidor;
 *                 obrigatório e não pode estar em branco
 */
public record LocalConfig(
        @NotBlank(message = "O rootPath é obrigatório para o provider LOCAL")
        String rootPath
) {
}
