package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.TokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TokenService tokens;

    AuthController(TokenService tokens) {
        this.tokens = tokens;
    }

    /** The one endpoint that cannot require a token. */
    @PostMapping("/token")
    TokenResponse token(@Valid @RequestBody TokenRequest request) {
        return tokens.issue(request);
    }
}
