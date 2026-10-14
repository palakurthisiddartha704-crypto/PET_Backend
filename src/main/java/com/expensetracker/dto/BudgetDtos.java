package com.expensetracker.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BudgetDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BudgetRequest {
        @NotNull(message = "Category ID is required")
        private Long categoryId;

        @NotNull(message = "Monthly limit is required")
        @DecimalMin(value = "0.01", message = "Budget limit must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Limit format is invalid")
        private BigDecimal monthlyLimit;

        @NotNull(message = "Month is required")
        @Min(value = 1, message = "Month must be between 1 and 12")
        @Max(value = 12, message = "Month must be between 1 and 12")
        private Integer month;

        @NotNull(message = "Year is required")
        @Min(value = 2000, message = "Year must be valid")
        private Integer year;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BudgetResponse {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryIcon;
        private BigDecimal monthlyLimit;
        private Integer month;
        private Integer year;
        private BigDecimal spent;
        private BigDecimal remaining;
        private Double percentage;
        private Boolean isWarning;
        private Boolean isExceeded;
        private LocalDateTime createdAt;
    }
}
