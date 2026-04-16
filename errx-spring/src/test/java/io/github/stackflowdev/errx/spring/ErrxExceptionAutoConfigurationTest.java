package io.github.stackflowdev.errx.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.*;

class ErrxExceptionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ErrxExceptionAutoConfiguration.class));

    @Test
    void registersErrxExceptionHandler() {
        runner.run(context -> assertThat(context).hasSingleBean(ErrxExceptionHandler.class));
    }

    @Test
    void backsOffWhenUserDefinesOwnBean() {
        runner.withUserConfiguration(CustomHandlerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ErrxExceptionHandler.class);
                    assertThat(context.getBean(ErrxExceptionHandler.class))
                            .isSameAs(context.getBean("customErrxExceptionHandler"));
                });
    }

    @Configuration
    static class CustomHandlerConfig {
        @Bean
        ErrxExceptionHandler customErrxExceptionHandler() {
            return new ErrxExceptionHandler();
        }
    }
}
