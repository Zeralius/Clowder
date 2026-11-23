package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.solver.HeuristicSolver;
import dev.zeralius.clowder.planning.solver.Solver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The solver is plain Java and knows nothing about Spring, so the container is told about it here, at
 * the edge, the same way the Solomon parser is.
 */
@Configuration(proxyBeanMethods = false)
class SolverConfiguration {

    @Bean
    Solver solver() {
        return new HeuristicSolver();
    }
}
