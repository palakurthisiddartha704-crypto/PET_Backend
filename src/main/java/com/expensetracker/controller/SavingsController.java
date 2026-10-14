package com.expensetracker.controller;

import com.expensetracker.security.CustomUserDetails;
import com.expensetracker.service.SavingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
public class SavingsController {

    private final SavingsService savingsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSavings(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(savingsService.getOverview(user.getId()));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Map<String, Object>> deposit(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, BigDecimal> request
    ) {
        return ResponseEntity.ok(savingsService.deposit(user.getId(), request.get("amount")));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, BigDecimal> request
    ) {
        return ResponseEntity.ok(savingsService.withdraw(user.getId(), request.get("amount")));
    }
}
