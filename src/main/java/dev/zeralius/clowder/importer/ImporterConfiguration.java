package dev.zeralius.clowder.importer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps {@link SolomonInstanceParser} free of Spring annotations while still making it injectable. */
@Configuration(proxyBeanMethods = false)
class ImporterConfiguration {

    @Bean
    SolomonInstanceParser solomonInstanceParser() {
        return new SolomonInstanceParser();
    }
}
