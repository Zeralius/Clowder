package dev.zeralius.clowder.execution.domain;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock bean, so nothing in execution has to reach for the system time directly. */
@Configuration(proxyBeanMethods = false)
class ExecutionConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
