package com.spendsmart.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Starts and ends authenticated browser sessions for the JSON auth endpoints. */
@Component
public class SessionManager {

    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final String sessionCookieName;

    public SessionManager(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository,
                          ServerProperties serverProperties) {
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        String name = serverProperties.getServlet().getSession().getCookie().getName();
        this.sessionCookieName = name == null ? "JSESSIONID" : name;
    }

    /**
     * Stores the principal in the session. The session id is rotated (session-fixation protection) and a fresh CSRF
     * token is issued, so nothing obtained before sign-in remains valid after it.
     */
    public void establish(AuthenticatedUser principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        rotateCsrf(request, response);
    }

    public void terminate(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie(sessionCookieName, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        rotateCsrf(request, response);
    }

    private void rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken fresh = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(fresh, request, response);
    }
}
