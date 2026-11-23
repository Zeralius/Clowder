package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.AppUserService;
import dev.zeralius.clowder.security.domain.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TokenService tokens;
    private final AppUserService users;

    AuthController(TokenService tokens, AppUserService users) {
        this.tokens = tokens;
        this.users = users;
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

    /**
     * Signs the caller out of every device, including whichever one sent this.
     *
     * <p>Needs a token, so it is the one endpoint here that cannot be reached without already being
     * signed in. It takes no body and no id: a request to sign somebody else out is an admin's, and
     * lives on {@code /api/users/{id}/sign-out} where the filter chain can say so.
     */
    @PostMapping("/sign-out")
    ResponseEntity<Void> signOut() {
        users.signOutCaller();
        return ResponseEntity.noContent().build();
    }
}
