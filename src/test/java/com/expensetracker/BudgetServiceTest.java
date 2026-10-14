package com.expensetracker;

import com.expensetracker.dto.BudgetDtos.BudgetResponse;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.service.BudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    @DisplayName("Calculate budget spent, remaining, and 85% warning threshold accurately")
    void shouldComputeBudgetUtilizationAndWarning() {
        User user = User.builder().id(1L).build();
        Category cat = Category.builder().id(5L).user(user).name("Dining").type(TransactionType.EXPENSE).displayColor("#ef4444").build();
        Budget budget = Budget.builder()
                .id(1L)
                .user(user)
                .category(cat)
                .monthlyLimit(new BigDecimal("500.00"))
                .month(10)
                .year(2026)
                .createdAt(LocalDateTime.now())
                .build();

        when(budgetRepository.findByUserIdAndMonthAndYear(1L, 10, 2026)).thenReturn(List.of(budget));
        // Spent 425 of 500 = 85.0%
        when(transactionRepository.sumExpenseByUserIdAndCategoryAndDateBetween(
                eq(1L), eq(5L), any(LocalDate.class), any(LocalDate.class)
        )).thenReturn(new BigDecimal("425.00"));

        List<BudgetResponse> results = budgetService.getBudgets(1L, 10, 2026);

        assertEquals(1, results.size());
        BudgetResponse res = results.get(0);
        assertEquals(new BigDecimal("425.00"), res.getSpent());
        assertEquals(new BigDecimal("75.00"), res.getRemaining());
        assertEquals(85.0, res.getPercentage());
        assertTrue(res.getIsWarning());
        assertFalse(res.getIsExceeded());
    }
}
