package com.expensetracker.dto;

import com.expensetracker.entity.Frequency;
import com.expensetracker.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class RecurringDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecurringRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        private String title;

        @NotNull(message = "Type is required")
        private TransactionType type;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        private BigDecimal amount;

        @NotNull(message = "Category ID is required")
        private Long categoryId;

        @NotNull(message = "Frequency is required (WEEKLY, MONTHLY, or YEARLY)")
        private Frequency frequency;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        private LocalDate endDate;
        private LocalDate nextExecutionDate;
        @Builder.Default
        private Boolean active = true;
        @Builder.Default
        private Boolean subscription = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecurringResponse {
        private Long id;
        private String title;
        private TransactionType type;
        private BigDecimal amount;
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryIcon;
        private Frequency frequency;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate nextExecutionDate;
        private Boolean active;
        private Boolean subscription;
        private LocalDate lastExecutedAt;
        private LocalDateTime createdAt;
    }
}
