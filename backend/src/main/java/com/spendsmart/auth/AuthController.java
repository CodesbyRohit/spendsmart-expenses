package com.spendsmart.auth;

import com.spendsmart.auth.AuthDtos.LoginRequest;
import com.spendsmart.auth.AuthDtos.RegisterRequest;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.security.SessionManager;
import com.spendsmart.user.UserLookup;
import com.spendsmart.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionManager sessions;
    private final UserLookup userLookup;

    public AuthController(AuthService authService, SessionManager sessions, UserLookup userLookup) {
        this.authService = authService;
        this.sessions = sessions;
        this.userLookup = userLookup;
    }

    /** Ensures the XSRF-TOKEN cookie exists before the SPA's first state-changing request. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf() {
        // The CSRF filter writes the cookie; nothing else to do.
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
                                 HttpServletResponse response) {
        AuthenticatedUser principal = authService.register(request, http.getRemoteAddr());
        sessions.establish(principal, http, response);
        return UserResponse.of(userLookup.require(principal.id()));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                              HttpServletResponse response) {
        AuthenticatedUser principal = authService.authenticate(request.email(), request.password(), http.getRemoteAddr());
        sessions.establish(principal, http, response);
        return UserResponse.of(userLookup.require(principal.id()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http, HttpServletResponse response) {
        sessions.terminate(http, response);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.of(userLookup.require(user.id()));
    }
}
