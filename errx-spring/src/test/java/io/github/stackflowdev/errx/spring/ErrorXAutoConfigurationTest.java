package io.github.stackflowdev.errx.spring;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.*;

class ErrorXAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ErrorXAutoConfiguration.class));

    // ── Auto-configuration ──────────────────────────────────────────

    @Nested
    class AutoConfigTests {

        @Test
        void registersErrorXHandler() {
            runner.run(context -> {
                assertThat(context).hasSingleBean(ErrorXHandler.class);
            });
        }

        @Test
        void backsOffWhenUserDefinesOwnBean() {
            runner.withUserConfiguration(CustomHandlerConfig.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ErrorXHandler.class);
                        // Should use the user's custom bean, not auto-configured one
                        assertThat(context.getBean(ErrorXHandler.class))
                                .isSameAs(context.getBean("customErrorXHandler"));
                    });
        }
    }

    @Configuration
    static class CustomHandlerConfig {
        @Bean
        ErrorXHandler customErrorXHandler() {
            return new ErrorXHandler();
        }
    }
}
