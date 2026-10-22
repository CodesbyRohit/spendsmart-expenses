package com.spendsmart.user;

import com.spendsmart.auth.PasswordPolicy;
import com.spendsmart.budget.CategoryBudgetRepository;
import com.spendsmart.budget.MonthlyBudgetRepository;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Money;
import com.spendsmart.common.Text;
import com.spendsmart.goal.GoalContributionRepository;
import com.spendsmart.goal.SavingsGoalRepository;
import com.spendsmart.notification.NotificationRepository;
import com.spendsmart.security.AuthRateLimiter;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.user.AccountDtos.ChangePasswordRequest;
import com.spendsmart.user.AccountDtos.DeleteAccountRequest;
import com.spendsmart.user.AccountDtos.UpdatePreferencesRequest;
import com.spendsmart.user.AccountDtos.UpdateProfileRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final UserLookup userLookup;
    private final PasswordEncoder passwordEncoder;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final MonthlyBudgetRepository budgets;
    private final CategoryBudgetRepository categoryBudgets;
    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final NotificationRepository notifications;
    private final AuthRateLimiter rateLimiter;

    public AccountService(UserRepository users, UserLookup userLookup, PasswordEncoder passwordEncoder,
                          TransactionRepository transactions, CategoryRepository categories,
                          MonthlyBudgetRepository budgets, CategoryBudgetRepository categoryBudgets,
                          SavingsGoalRepository goals, GoalContributionRepository contributions,
                          NotificationRepository notifications, AuthRateLimiter rateLimiter) {
        this.users = users;
        this.userLookup = userLookup;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.categories = categories;
        this.budgets = budgets;
        this.categoryBudgets = categoryBudgets;
        this.goals = goals;
        this.contributions = contributions;
        this.notifications = notifications;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userLookup.require(userId);
        String name = Text.clean(request.displayName());
        if (name == null) {
            throw new FieldValidationException("displayName", "Enter your name.");
        }
        String email = Text.normalizeEmail(request.email());
        if (!email.equals(user.getEmail())) {
            requirePassword(user, request.currentPassword(), "currentPassword");
            if (users.existsByEmail(email)) {
                throw new FieldValidationException("email", "Another account already uses this email.");
            }
            user.setEmail(email);
        }
        user.setDisplayName(name);
        return UserResponse.of(users.saveAndFlush(user));
    }

    @Transactional
    public UserResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        User user = userLookup.require(userId);
        user.setDefaultCurrency(Money.requireCurrency(request.defaultCurrency(), "defaultCurrency"));
        user.setLocale(Locales.require(request.locale(), "locale"));
        user.setTheme(request.theme());
        user.setBudgetAlertsEnabled(request.budgetAlertsEnabled());
        user.setBudgetAlertThreshold(request.budgetAlertThreshold());
        return UserResponse.of(users.saveAndFlush(user));
    }

    /**
     * Changes the password and bumps the credential version, which invalidates every other signed-in session.
     * Returns the new principal so the caller can keep the current session signed in.
     */
    @Transactional
    public AuthenticatedUser changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userLookup.require(userId);
        requirePassword(user, request.currentPassword(), "currentPassword");
        PasswordPolicy.check(request.newPassword(), user.getEmail(), "newPassword");
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("newPassword", "Choose a password different from your current one.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        User saved = users.saveAndFlush(user);
        return new AuthenticatedUser(saved.getId(), saved.getEmail(), saved.getCredentialVersion());
    }

    /**
     * Permanently deletes the account and every record it owns, children first, in one transaction.
     * There is no soft delete and no recovery; the UI offers a full data export before this step.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = userLookup.require(userId);
        if (!"DELETE".equals(request.confirmation())) {
            throw new FieldValidationException("confirmation", "Type DELETE in capital letters to confirm.");
        }
        requirePassword(user, request.password(), "password");
        notifications.deleteAllForUser(userId);
        contributions.deleteAllForUser(userId);
        goals.deleteAllForUser(userId);
        categoryBudgets.deleteAllForUser(userId);
        budgets.deleteAllForUser(userId);
        transactions.deleteAllForUser(userId);
        categories.deleteAll(categories.findByUserIdOrderByTypeAscNameAsc(userId));
        categories.flush();
        users.delete(userLookup.require(userId));
        log.info("Account {} deleted at the owner's request", userId);
    }

    private void requirePassword(User user, String password, String field) {
        rateLimiter.checkPasswordConfirmation(user.getId());
        if (password == null || password.isEmpty() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.recordPasswordConfirmationFailure(user.getId());
            throw new FieldValidationException(field, "Password is incorrect.");
        }
    }
}
