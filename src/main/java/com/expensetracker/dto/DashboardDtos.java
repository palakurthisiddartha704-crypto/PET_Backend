package com.expensetracker.dto;

import com.expensetracker.dto.TransactionDtos.TransactionResponse;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummaryResponse {
        private Integer month;
        private Integer year;
        private BigDecimal totalIncome;
        private BigDecimal totalExpenses;
        private BigDecimal remainingBalance;
        private BigDecimal totalSavings;
        private BigDecimal monthlyAvailableToSave;
        private BigDecimal totalBudgetLimit;
        private BigDecimal totalBudgetSpent;
        private Double overallBudgetUtilization;
        private List<TransactionResponse> recentTransactions;
        private List<CategorySpendingItem> topSpendingCategories;
        private List<BudgetAlertItem> budgetAlerts;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySpendingItem {
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryIcon;
        private BigDecimal amount;
        private Double percentage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BudgetAlertItem {
        private Long categoryId;
        private String categoryName;
        private BigDecimal monthlyLimit;
        private BigDecimal spent;
        private Double percentage;
        private Boolean isWarning;
        private Boolean isExceeded;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryBreakdownResponse {
        private BigDecimal totalExpense;
        private List<CategorySpendingItem> categories;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyTrendItem {
        private Integer monthNumber;
        private String month;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal savings;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyTrendResponse {
        private Integer year;
        private List<MonthlyTrendItem> trend;
    }
}
