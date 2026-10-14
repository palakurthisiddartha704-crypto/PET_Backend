package com.expensetracker.controller;

import com.expensetracker.dto.RecurringDtos.*;
import com.expensetracker.security.CustomUserDetails;
import com.expensetracker.service.RecurringTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recurring-transactions")
@RequiredArgsConstructor
public class RecurringTransactionController {

    private final RecurringTransactionService recurringService;

    @GetMapping
    public ResponseEntity<List<RecurringResponse>> getRecurringRules(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(recurringService.getRecurringRules(userDetails.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringResponse> getRecurringRuleById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(recurringService.getRecurringRuleById(userDetails.getId(), id));
    }

    @PostMapping
    public ResponseEntity<RecurringResponse> createRecurringRule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RecurringRequest request
    ) {
        RecurringResponse response = recurringService.createRecurringRule(userDetails.getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringResponse> updateRecurringRule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RecurringRequest request
    ) {
        return ResponseEntity.ok(recurringService.updateRecurringRule(userDetails.getId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RecurringResponse> toggleStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> payload
    ) {
        Boolean active = (payload != null) ? payload.get("active") : null;
        return ResponseEntity.ok(recurringService.toggleStatus(userDetails.getId(), id, active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRecurringRule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        recurringService.deleteRecurringRule(userDetails.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Recurring rule deleted successfully"));
    }
}
