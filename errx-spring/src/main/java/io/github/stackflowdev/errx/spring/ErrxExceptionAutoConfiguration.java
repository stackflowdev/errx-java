package io.github.stackflowdev.errx.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for errx-spring.
 *
 * <p>Registers {@link ErrxExceptionHandler} as a bean when the user hasn't
 * defined their own. If a {@link MessageSource} is present in the context,
 * it is injected for i18n resolution; otherwise the handler falls back to
 * raw codes as messages.
 *
 * <p>To customize, define your own {@link ErrxExceptionHandler} bean —
 * this auto-configuration will back off.
 */
@AutoConfiguration
public class ErrxExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrxExceptionHandler errxExceptionHandler(ObjectProvider<MessageSource> messageSource) {
        return new ErrxExceptionHandler(messageSource.getIfAvailable());
    }
}
