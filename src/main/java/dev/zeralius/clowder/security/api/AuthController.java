package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.TokenService;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * The one endpoint that cannot require a token, which is why it is also the one that is rate
     * limited.
     *
     * <p>The address comes from {@code getRemoteAddr} and no header is consulted. Trusting a
     * client-supplied {@code X-Forwarded-For} would make the per-address budget free to bypass: send a
     * different one each time and every attempt looks like a new caller. Behind a reverse proxy that
     * overwrites the header, set {@code server.forward-headers-strategy} and this reads the real
     * client again, because then it is the proxy saying it rather than the client.
     */
    @PostMapping("/token")
    TokenResponse token(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        return tokens.issue(request, http.getRemoteAddr());
    }
}
