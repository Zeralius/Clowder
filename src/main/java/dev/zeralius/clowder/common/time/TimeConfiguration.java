package dev.zeralius.clowder.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock bean for the whole application, so nothing reaches for the system time directly and a
 * test can wind it forward.
 *
 * <p>It lives in the shared kernel rather than in whichever module needed it first. It began in
 * {@code execution}, which meant that everything else asking for a {@link Clock} was quietly wired to
 * a bean belonging to another module — harmless until that module moves.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
