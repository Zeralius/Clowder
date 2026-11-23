package dev.zeralius.clowder.common.error;

import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error path in the application returns the same RFC 9457 envelope, including bean-validation
 * failures.
 *
 * <p>There is deliberately no {@code IllegalArgumentException} handler: mapping it to 400 turns
 * programming errors into innocuous-looking client errors and you stop seeing them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI NOT_FOUND_TYPE = URI.create("https://clowder.eu/errors/not-found");
    private static final URI CONFLICT_TYPE = URI.create("https://clowder.eu/errors/conflict");
    private static final URI VALIDATION_TYPE = URI.create("https://clowder.eu/errors/validation");

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setType(NOT_FOUND_TYPE);
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setType(CONFLICT_TYPE);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleStaleWrite(OptimisticLockingFailureException ex) {
        log.debug("Optimistic locking failure", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This record was modified by someone else. Reload and retry");
        problem.setTitle("Concurrent modification");
        problem.setType(CONFLICT_TYPE);
        return problem;
    }

    /**
     * The lost race against a pre-check such as {@code existsByRegistration}, and every other
     * constraint. Detail is logged, not returned: constraint names describe the schema.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with data that already exists");
        problem.setTitle("Conflict");
        problem.setType(CONFLICT_TYPE);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setType(VALIDATION_TYPE);
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> Map.of(
                                "field",
                                fe.getField(),
                                "message",
                                fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                        .toList());

        return ResponseEntity.badRequest().body(problem);
    }
}
