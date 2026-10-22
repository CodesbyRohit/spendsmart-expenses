package com.spendsmart.transaction;

import com.spendsmart.common.PageResponse;
import com.spendsmart.common.TransactionType;
import com.spendsmart.export.TransactionCsvExporter;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.transaction.TransactionDtos.TransactionRequest;
import com.spendsmart.transaction.TransactionDtos.TransactionResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;
    private final TransactionCsvExporter csvExporter;

    public TransactionController(TransactionService service, TransactionCsvExporter csvExporter) {
        this.service = service;
        this.csvExporter = csvExporter;
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return service.list(user.id(), new TransactionFilter(query, type, categoryId, currency, from, to), page, size, sort);
    }

    @GetMapping("/currencies")
    public List<String> currencies(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.currencies(user.id());
    }

    /** Same filters as the list endpoint; returns every matching row (up to the export cap) as CSV. */
    // No "produces" restriction: the SPA sends Accept: application/json on every request, and a text/csv-only
    // mapping made Spring fall through to /{id} with id="export" (400). The handler sets text/csv itself.
    @GetMapping("/export")
    public void export(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {
        List<Transaction> rows = service.exportRows(user.id(),
                new TransactionFilter(query, type, categoryId, currency, from, to));
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"spendsmart-transactions-" + LocalDate.now() + ".csv\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write('﻿'); // BOM so Excel opens UTF-8 (₹, €) correctly
        csvExporter.write(rows, writer);
        writer.flush();
    }

    @GetMapping("/{id:[0-9a-fA-F-]+}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.get(user.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody TransactionRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id:[0-9a-fA-F-]+}")
    public TransactionResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                      @Valid @RequestBody TransactionRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id:[0-9a-fA-F-]+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }
}
