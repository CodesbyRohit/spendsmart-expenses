package com.spendsmart.auth;

import com.spendsmart.auth.AuthDtos.RegisterRequest;
import com.spendsmart.category.CategoryService;
import com.spendsmart.common.ApiException;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Money;
import com.spendsmart.common.Text;
import com.spendsmart.security.AuthRateLimiter;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.user.Locales;
import com.spendsmart.user.User;
import com.spendsmart.user.UserClock;
import com.spendsmart.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CategoryService categoryService;
    private final AuthRateLimiter rateLimiter;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       CategoryService categoryService, AuthRateLimiter rateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.categoryService = categoryService;
        this.rateLimiter = rateLimiter;
    }

    /** Creates the account and its default categories atomically. */
    @Transactional
    public AuthenticatedUser register(RegisterRequest request, String clientIp) {
        rateLimiter.checkAndRecordRegistration(clientIp);
        String email = Text.normalizeEmail(request.email());
        PasswordPolicy.check(request.password(), email, "password");
        String currency = Money.requireCurrency(request.defaultCurrency(), "defaultCurrency");
        String locale = Locales.require(request.locale(), "locale");
        String name = Text.clean(request.displayName());
        if (name == null) {
            throw new FieldValidationException("displayName", "Enter your name.");
        }
        if (users.existsByEmail(email)) {
            throw new FieldValidationException("email", "An account with this email already exists. Sign in instead.");
        }
        User newUser = new User(email, passwordEncoder.encode(request.password()), name, currency, locale);
        newUser.setTimeZone(UserClock.isValidZone(request.timeZone()) ? request.timeZone() : UserClock.DEFAULT_ZONE);
        User user = users.saveAndFlush(newUser);
        categoryService.seedDefaults(user.getId());
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getCredentialVersion());
    }

    /**
     * Verifies credentials. Failures return one generic message whether the email exists or not, and count toward
     * the per-email and per-IP limits.
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String rawEmail, String password, String clientIp) {
        String email = Text.normalizeEmail(rawEmail);
        rateLimiter.checkLogin(email, clientIp);
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            rateLimiter.recordLoginFailure(email, clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        rateLimiter.recordLoginSuccess(email);
        User user = users.findByEmail(email).orElseThrow();
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getCredentialVersion());
    }
}
