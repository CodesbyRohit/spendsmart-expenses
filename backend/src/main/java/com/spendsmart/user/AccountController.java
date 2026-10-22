package com.spendsmart.user;

import com.spendsmart.common.Money;
import com.spendsmart.export.AccountExportService;
import com.spendsmart.export.AccountExportService.AccountExport;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.security.SessionManager;
import com.spendsmart.user.AccountDtos.ChangePasswordRequest;
import com.spendsmart.user.AccountDtos.DeleteAccountRequest;
import com.spendsmart.user.AccountDtos.UpdatePreferencesRequest;
import com.spendsmart.user.AccountDtos.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService service;
    private final AccountExportService exportService;
    private final SessionManager sessions;
    private final UserLookup userLookup;

    public AccountController(AccountService service, AccountExportService exportService, SessionManager sessions,
                             UserLookup userLookup) {
        this.service = service;
        this.exportService = exportService;
        this.sessions = sessions;
        this.userLookup = userLookup;
    }

    @GetMapping
    public UserResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.of(userLookup.require(user.id()));
    }

    /** Options for the settings forms, so the frontend never hard-codes a list the server would reject. */
    @GetMapping("/options")
    public Map<String, List<String>> options() {
        return Map.of("currencies", Money.SUPPORTED_CURRENCIES, "locales", Locales.SUPPORTED,
                "timeZones", UserClock.SUPPORTED_ZONES);
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(user.id(), request);
    }

    @PutMapping("/preferences")
    public UserResponse updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody UpdatePreferencesRequest request) {
        return service.updatePreferences(user.id(), request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest http, HttpServletResponse response) {
        AuthenticatedUser updated = service.changePassword(user.id(), request);
        sessions.establish(updated, http, response);
    }

    @GetMapping("/export")
    public ResponseEntity<AccountExport> export(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"spendsmart-account-" + LocalDate.now() + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exportService.export(user.id()));
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DeleteAccountRequest request,
                       HttpServletRequest http, HttpServletResponse response) {
        service.deleteAccount(user.id(), request);
        sessions.terminate(http, response);
    }
}
