package com.spendsmart.budget;

import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.budget.BudgetDtos.HistoryEntry;
import com.spendsmart.budget.BudgetDtos.SaveBudgetRequest;
import com.spendsmart.common.Months;
import com.spendsmart.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping("/currencies")
    public List<String> currencies(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.currencies(user.id());
    }

    @GetMapping("/history")
    public List<HistoryEntry> history(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam String currency,
                                      @RequestParam(defaultValue = "12") int months) {
        return service.history(user.id(), currency, months);
    }

    @GetMapping("/{month}")
    public BudgetMonthResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String month,
                                   @RequestParam String currency) {
        return service.status(user.id(), Months.parse(month, "month"), currency);
    }

    @PutMapping("/{month}")
    public BudgetMonthResponse save(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String month,
                                    @Valid @RequestBody SaveBudgetRequest request) {
        return service.save(user.id(), Months.parse(month, "month"), request);
    }

    @PostMapping("/{month}/copy-previous")
    public BudgetMonthResponse copyPrevious(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String month,
                                            @RequestParam String currency) {
        return service.copyPrevious(user.id(), Months.parse(month, "month"), currency);
    }

    @DeleteMapping("/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String month,
                       @RequestParam String currency) {
        service.delete(user.id(), Months.parse(month, "month"), currency);
    }
}
