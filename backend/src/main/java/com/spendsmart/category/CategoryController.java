package com.spendsmart.category;

import com.spendsmart.category.CategoryDtos.CategoryResponse;
import com.spendsmart.category.CategoryDtos.CategoryUsageResponse;
import com.spendsmart.category.CategoryDtos.CreateCategoryRequest;
import com.spendsmart.category.CategoryDtos.UpdateCategoryRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(user.id(), includeArchived);
    }

    @GetMapping("/icons")
    public List<String> icons() {
        return CategoryIcons.ALL;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CreateCategoryRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateCategoryRequest request) {
        return service.update(user.id(), id, request);
    }

    @GetMapping("/{id}/usage")
    public CategoryUsageResponse usage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.usage(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                       @RequestParam(required = false) UUID reassignTo) {
        service.delete(user.id(), id, reassignTo);
    }
}
