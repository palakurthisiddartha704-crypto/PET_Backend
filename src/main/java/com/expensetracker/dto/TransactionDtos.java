package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TransactionDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionRequest {
        @NotNull(message = "Transaction type is required (INCOME or EXPENSE)")
        private TransactionType type;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
        private BigDecimal amount;

        @NotBlank(message = "Title / description is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        private String title;

        @NotNull(message = "Category ID is required")
        private Long categoryId;

        @NotNull(message = "Transaction date is required")
        private LocalDate transactionDate;

        @NotBlank(message = "Payment method is required")
        private String paymentMethod;

        private String notes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionResponse {
        private Long id;
        private TransactionType type;
        private BigDecimal amount;
        private String title;
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryIcon;
        private LocalDate transactionDate;
        private String paymentMethod;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
