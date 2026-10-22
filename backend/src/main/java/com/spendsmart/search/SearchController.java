package com.spendsmart.search;

import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.search.SearchService.SearchResults;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchResults search(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam("q") String query) {
        return service.search(user.id(), query);
    }
}
