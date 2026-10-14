package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.BudgetGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

public class CategoryDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryRequest {
        @NotBlank(message = "Category name is required")
        @Size(max = 80, message = "Name cannot exceed 80 characters")
        private String name;

        @NotNull(message = "Category type is required (INCOME or EXPENSE)")
        private TransactionType type;

        private String description;
        private String displayColor;
        private String icon;
        private BudgetGroup budgetGroup;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryResponse {
        private Long id;
        private String name;
        private TransactionType type;
        private String description;
        private String displayColor;
        private String icon;
        private BudgetGroup budgetGroup;
        private Boolean isDefault;
    }
}
