package com.expensetracker.service;

import com.expensetracker.dto.BudgetDtos.*;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets(Long userId, Integer month, Integer year) {
        YearMonth ym = (month != null && year != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, ym.getMonthValue(), ym.getYear());

        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        return budgets.stream().map(b -> {
            BigDecimal spent = transactionRepository.sumExpenseByUserIdAndCategoryAndDateBetween(
                    userId, b.getCategory().getId(), startDate, endDate
            );

            BigDecimal remaining = b.getMonthlyLimit().subtract(spent);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }

            double percentage = 0.0;
            if (b.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
                percentage = spent.divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            boolean isWarning = percentage >= 80.0 && percentage <= 100.0;
            boolean isExceeded = percentage > 100.0;

            return BudgetResponse.builder()
                    .id(b.getId())
                    .categoryId(b.getCategory().getId())
                    .categoryName(b.getCategory().getName())
                    .categoryColor(b.getCategory().getDisplayColor())
                    .categoryIcon(b.getCategory().getIcon())
                    .monthlyLimit(b.getMonthlyLimit())
                    .month(b.getMonth())
                    .year(b.getYear())
                    .spent(spent)
                    .remaining(remaining)
                    .percentage(Math.round(percentage * 10.0) / 10.0)
                    .isWarning(isWarning)
                    .isExceeded(isExceeded)
                    .createdAt(b.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public BudgetResponse createBudget(Long userId, BudgetRequest request) {
        if (budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(
                userId, request.getCategoryId(), request.getMonth(), request.getYear())) {
            throw new BadRequestException("A budget limit already exists for this category in the specified month and year");
        }

        Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                .orElseThrow(() -> new BadRequestException("Category not found or does not belong to user"));

        if (category.getType() != TransactionType.EXPENSE) {
            throw new BadRequestException("Budgets can only be created for EXPENSE categories");
        }

        User user = userRepository.getReferenceById(userId);

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .monthlyLimit(request.getMonthlyLimit())
                .month(request.getMonth())
                .year(request.getYear())
                .build();

        Budget saved = budgetRepository.save(budget);

        YearMonth ym = YearMonth.of(saved.getYear(), saved.getMonth());
        BigDecimal spent = transactionRepository.sumExpenseByUserIdAndCategoryAndDateBetween(
                userId, category.getId(), ym.atDay(1), ym.atEndOfMonth()
        );

        BigDecimal remaining = saved.getMonthlyLimit().subtract(spent).max(BigDecimal.ZERO);
        double percentage = saved.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(saved.getMonthlyLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return BudgetResponse.builder()
                .id(saved.getId())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .categoryColor(category.getDisplayColor())
                .categoryIcon(category.getIcon())
                .monthlyLimit(saved.getMonthlyLimit())
                .month(saved.getMonth())
                .year(saved.getYear())
                .spent(spent)
                .remaining(remaining)
                .percentage(Math.round(percentage * 10.0) / 10.0)
                .isWarning(percentage >= 80.0 && percentage <= 100.0)
                .isExceeded(percentage > 100.0)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public BudgetResponse updateBudget(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found with ID: " + budgetId));

        if (request.getMonthlyLimit() != null) {
            budget.setMonthlyLimit(request.getMonthlyLimit());
        }

        Budget saved = budgetRepository.save(budget);
        YearMonth ym = YearMonth.of(saved.getYear(), saved.getMonth());
        BigDecimal spent = transactionRepository.sumExpenseByUserIdAndCategoryAndDateBetween(
                userId, saved.getCategory().getId(), ym.atDay(1), ym.atEndOfMonth()
        );

        BigDecimal remaining = saved.getMonthlyLimit().subtract(spent).max(BigDecimal.ZERO);
        double percentage = saved.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(saved.getMonthlyLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return BudgetResponse.builder()
                .id(saved.getId())
                .categoryId(saved.getCategory().getId())
                .categoryName(saved.getCategory().getName())
                .categoryColor(saved.getCategory().getDisplayColor())
                .categoryIcon(saved.getCategory().getIcon())
                .monthlyLimit(saved.getMonthlyLimit())
                .month(saved.getMonth())
                .year(saved.getYear())
                .spent(spent)
                .remaining(remaining)
                .percentage(Math.round(percentage * 10.0) / 10.0)
                .isWarning(percentage >= 80.0 && percentage <= 100.0)
                .isExceeded(percentage > 100.0)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteBudget(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found with ID: " + budgetId));
        budgetRepository.delete(budget);
    }
}
