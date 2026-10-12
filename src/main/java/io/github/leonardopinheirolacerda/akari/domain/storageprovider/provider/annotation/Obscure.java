package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um campo de um record de config de provider (ex.: {@code MegaConfig.password}) como
 * sensível em claro, exigindo obscurecimento antes de persistir.
 *
 * <p>Processada por {@code ObscureFieldApplier}, que localiza via reflection todo campo anotado
 * no {@code configType} do provider e substitui o valor em claro pelo retornado por
 * {@code /core/obscure} do rclone, antes de validar e gravar a config.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Obscure {
}
