package com.spendsmart.sample;

import com.spendsmart.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample-data")
public class SampleDataController {

    private final SampleDataService service;

    public SampleDataController(SampleDataService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> status(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("present", service.isPresent(user.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SampleDataService.SampleSummary load(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.load(user.id());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user) {
        service.remove(user.id());
    }
}
