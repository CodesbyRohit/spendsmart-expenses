package com.spendsmart.goal;

import com.spendsmart.goal.GoalDtos.ContributionRequest;
import com.spendsmart.goal.GoalDtos.ContributionResponse;
import com.spendsmart.goal.GoalDtos.CreateGoalRequest;
import com.spendsmart.goal.GoalDtos.GoalResponse;
import com.spendsmart.goal.GoalDtos.UpdateGoalRequest;
import com.spendsmart.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @GetMapping("/{id}")
    public GoalResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.get(user.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody CreateGoalRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody UpdateGoalRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> contributions(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable UUID id) {
        return service.contributions(user.id(), id);
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse addContribution(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                        @Valid @RequestBody ContributionRequest request) {
        return service.addContribution(user.id(), id, request);
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public GoalResponse deleteContribution(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                           @PathVariable UUID contributionId) {
        return service.deleteContribution(user.id(), id, contributionId);
    }
}
