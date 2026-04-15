package io.github.stackflowdev.errx.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for errx-spring.
 *
 * <p>Registers {@link ErrorXHandler} as a bean if the user hasn't
 * defined their own. This allows zero-config usage: just add
 * {@code errx-spring} to your classpath and all {@code ErrorX}
 * exceptions will be handled automatically.
 *
 * <p>To customize, define your own {@link ErrorXHandler} bean —
 * this auto-configuration will back off.
 */
@AutoConfiguration
public class ErrorXAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrorXHandler errorXHandler() {
        return new ErrorXHandler();
    }
}
