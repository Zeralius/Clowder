package dev.zeralius.clowder.planning.plan.domain;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The pool that solves plan runs.
 *
 * <p>Deliberately small and deliberately bounded. A solve is CPU-bound for up to its budget, so more
 * threads than cores makes every run slower rather than the queue shorter, and an unbounded queue
 * turns a burst of requests into a memory leak that reports success. When the queue is full the
 * submission is rejected and the run is marked FAILED with a reason, which is a worse answer than
 * "solved" and a much better one than silence.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "clowder.planning")
public class PlanRunExecutorConfiguration {

    private int workers = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private int queueCapacity = 32;

    @Bean(name = "planRunExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor planRunExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("plan-run-");
        // Abort rather than run the solve on the caller thread: silently borrowing the request thread
        // under load is exactly the behaviour moving the solve off it was meant to stop.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Let an in-flight solve finish on shutdown, so a run does not end up stuck at RUNNING.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
