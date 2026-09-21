package dev.zeralius.clowder.importer;

import java.io.StringReader;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The body is the instance file itself: no instance files ship inside the application, no
 * name-to-path lookup, no second copy of the benchmark set under {@code src/main/resources}.
 *
 * <p>A malformed instance throws {@code IllegalArgumentException}, which deliberately has no handler
 * and becomes a 500. For a developer-facing import endpoint that is the right outcome: you want the
 * stack trace. If this is ever exposed further, wrap the parse and rethrow as a dedicated exception;
 * do not add a global {@code IllegalArgumentException} handler.
 */
@RestController
@RequestMapping("/api/import")
public class SolomonImportController {

    private final SolomonImportService importer;

    SolomonImportController(SolomonImportService importer) {
        this.importer = importer;
    }

    /**
     * POST the raw instance file as {@code text/plain}. The optional {@code serviceDate} overrides the
     * synthetic default; the format itself carries no date.
     */
    @PostMapping(value = "/solomon", consumes = MediaType.TEXT_PLAIN_VALUE)
    SolomonImportResult importSolomon(
            @RequestBody String instanceText,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return importer.importFrom(new StringReader(instanceText), serviceDate);
    }
}
