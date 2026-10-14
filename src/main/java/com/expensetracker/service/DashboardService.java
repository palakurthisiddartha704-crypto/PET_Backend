package com.expensetracker.service;

import com.expensetracker.dto.DashboardDtos.*;
import com.expensetracker.dto.TransactionDtos.TransactionResponse;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.SavingsEntryRepository;
import com.expensetracker.entity.SavingsEntryType;
import com.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
        private final SavingsEntryRepository savingsEntryRepository;

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(Long userId, Integer month, Integer year) {
        YearMonth ym = (month != null && year != null) ? YearMonth.of(year, month) : YearMonth.now();
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, startDate, endDate
        );
        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, startDate, endDate
        );

        BigDecimal monthNet = totalIncome.subtract(totalExpenses);
        BigDecimal manuallySavedThisMonth = savingsEntryRepository
                .findByUserIdAndEntryTypeAndPeriodYearAndPeriodMonth(
                        userId, SavingsEntryType.MANUAL_DEPOSIT, ym.getYear(), ym.getMonthValue())
                .stream().map(com.expensetracker.entity.SavingsEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingBalance = monthNet.subtract(manuallySavedThisMonth);
        BigDecimal totalSavings = savingsEntryRepository.sumBalanceByUserId(userId);

        // Budgets for period
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, ym.getMonthValue(), ym.getYear());
        BigDecimal totalBudgetLimit = budgets.stream()
                .map(Budget::getMonthlyLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetSpent = BigDecimal.ZERO;
        List<BudgetAlertItem> alerts = new ArrayList<>();

        for (Budget b : budgets) {
            BigDecimal spent = transactionRepository.sumExpenseByUserIdAndCategoryAndDateBetween(
                    userId, b.getCategory().getId(), startDate, endDate
            );
            totalBudgetSpent = totalBudgetSpent.add(spent);

            double percentage = b.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            boolean isWarning = percentage >= 80.0 && percentage <= 100.0;
            boolean isExceeded = percentage > 100.0;

            if (isWarning || isExceeded) {
                alerts.add(BudgetAlertItem.builder()
                        .categoryId(b.getCategory().getId())
                        .categoryName(b.getCategory().getName())
                        .monthlyLimit(b.getMonthlyLimit())
                        .spent(spent)
                        .percentage(Math.round(percentage * 10.0) / 10.0)
                        .isWarning(isWarning)
                        .isExceeded(isExceeded)
                        .build());
            }
        }

        double overallBudgetUtilization = totalBudgetLimit.compareTo(BigDecimal.ZERO) > 0
                ? totalBudgetSpent.divide(totalBudgetLimit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // Recent 6 transactions
        List<Transaction> recent = transactionRepository.findTop6ByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);
        List<TransactionResponse> recentResponses = recent.stream().map(t -> TransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .amount(t.getAmount())
                .title(t.getTitle())
                .categoryId(t.getCategory().getId())
                .categoryName(t.getCategory().getName())
                .categoryColor(t.getCategory().getDisplayColor())
                .categoryIcon(t.getCategory().getIcon())
                .transactionDate(t.getTransactionDate())
                .paymentMethod(t.getPaymentMethod())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build()).collect(Collectors.toList());

        // Top spending categories in this period
        List<Transaction> monthTransactions = transactionRepository.findByUserIdAndTransactionDateBetween(userId, startDate, endDate);
        Map<Long, BigDecimal> categorySpendMap = new HashMap<>();
        Map<Long, com.expensetracker.entity.Category> categoryRefMap = new HashMap<>();

        for (Transaction t : monthTransactions) {
            if (t.getType() == TransactionType.EXPENSE) {
                Long cId = t.getCategory().getId();
                categorySpendMap.put(cId, categorySpendMap.getOrDefault(cId, BigDecimal.ZERO).add(t.getAmount()));
                categoryRefMap.putIfAbsent(cId, t.getCategory());
            }
        }

        List<CategorySpendingItem> topSpending = categorySpendMap.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    com.expensetracker.entity.Category cat = categoryRefMap.get(e.getKey());
                    double pct = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 0.0;
                    return CategorySpendingItem.builder()
                            .categoryId(cat.getId())
                            .categoryName(cat.getName())
                            .categoryColor(cat.getDisplayColor())
                            .categoryIcon(cat.getIcon())
                            .amount(e.getValue())
                            .percentage(Math.round(pct * 10.0) / 10.0)
                            .build();
                }).collect(Collectors.toList());

        return SummaryResponse.builder()
                .month(ym.getMonthValue())
                .year(ym.getYear())
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .remainingBalance(remainingBalance)
                .totalSavings(totalSavings)
                .monthlyAvailableToSave(remainingBalance.max(BigDecimal.ZERO))
                .totalBudgetLimit(totalBudgetLimit)
                .totalBudgetSpent(totalBudgetSpent)
                .overallBudgetUtilization(Math.round(overallBudgetUtilization * 10.0) / 10.0)
                .recentTransactions(recentResponses)
                .topSpendingCategories(topSpending)
                .budgetAlerts(alerts)
                .build();
    }

    @Transactional(readOnly = true)
    public CategoryBreakdownResponse getCategoryBreakdown(Long userId, Integer month, Integer year) {
        YearMonth ym = (month != null && year != null) ? YearMonth.of(year, month) : YearMonth.now();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);
        BigDecimal totalExpenses = BigDecimal.ZERO;
        Map<Long, BigDecimal> spendMap = new HashMap<>();
        Map<Long, com.expensetracker.entity.Category> catMap = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.EXPENSE) {
                totalExpenses = totalExpenses.add(t.getAmount());
                Long cId = t.getCategory().getId();
                spendMap.put(cId, spendMap.getOrDefault(cId, BigDecimal.ZERO).add(t.getAmount()));
                catMap.putIfAbsent(cId, t.getCategory());
            }
        }

        final BigDecimal finalTotal = totalExpenses;
        List<CategorySpendingItem> items = spendMap.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    com.expensetracker.entity.Category cat = catMap.get(e.getKey());
                    double pct = finalTotal.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().divide(finalTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 0.0;
                    return CategorySpendingItem.builder()
                            .categoryId(cat.getId())
                            .categoryName(cat.getName())
                            .categoryColor(cat.getDisplayColor())
                            .categoryIcon(cat.getIcon())
                            .amount(e.getValue())
                            .percentage(Math.round(pct * 10.0) / 10.0)
                            .build();
                }).collect(Collectors.toList());

        return CategoryBreakdownResponse.builder()
                .totalExpense(totalExpenses)
                .categories(items)
                .build();
    }

    @Transactional(readOnly = true)
    public MonthlyTrendResponse getMonthlyTrend(Long userId, Integer year) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        List<MonthlyTrendItem> trend = new ArrayList<>();
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(targetYear, m);
            BigDecimal income = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.INCOME, ym.atDay(1), ym.atEndOfMonth()
            );
            BigDecimal expense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.EXPENSE, ym.atDay(1), ym.atEndOfMonth()
            );
            BigDecimal savings = income.subtract(expense);

            trend.add(MonthlyTrendItem.builder()
                    .monthNumber(m)
                    .month(monthNames[m - 1])
                    .income(income)
                    .expense(expense)
                    .savings(savings)
                    .build());
        }

        return MonthlyTrendResponse.builder()
                .year(targetYear)
                .trend(trend)
                .build();
    }
}
