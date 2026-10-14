package com.expensetracker.controller;

import com.expensetracker.entity.BudgetGroup;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TransactionRepository transactionRepository;

    @GetMapping("/calendar")
    public ResponseEntity<Map<String, Object>> getCalendar(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year
    ) {
        YearMonth period = month != null && year != null ? YearMonth.of(year, month) : YearMonth.now();
        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateBetween(
                user.getId(), period.atDay(1), period.atEndOfMonth());
        Map<LocalDate, Map<String, Object>> daily = new TreeMap<>();

        for (Transaction tx : transactions) {
            Map<String, Object> day = daily.computeIfAbsent(tx.getTransactionDate(), date -> {
                Map<String, Object> values = new HashMap<>();
                values.put("date", date);
                values.put("income", BigDecimal.ZERO);
                values.put("expense", BigDecimal.ZERO);
                values.put("count", 0);
                return values;
            });
            String amountKey = tx.getType() == TransactionType.INCOME ? "income" : "expense";
            day.put(amountKey, ((BigDecimal) day.get(amountKey)).add(tx.getAmount()));
            day.put("count", (Integer) day.get("count") + 1);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("month", period.getMonthValue());
        response.put("year", period.getYear());
        response.put("days", new ArrayList<>(daily.values()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> getComparison(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year
    ) {
        YearMonth currentPeriod = month != null && year != null ? YearMonth.of(year, month) : YearMonth.now();
        LocalDate today = LocalDate.now();
        int elapsedDays = currentPeriod.equals(YearMonth.from(today))
                ? today.getDayOfMonth()
                : currentPeriod.lengthOfMonth();
        LocalDate currentStart = currentPeriod.atDay(1);
        LocalDate currentEnd = currentPeriod.atDay(Math.min(elapsedDays, currentPeriod.lengthOfMonth()));
        YearMonth previousPeriod = currentPeriod.minusMonths(1);
        LocalDate previousStart = previousPeriod.atDay(1);
        LocalDate previousEnd = previousPeriod.atDay(Math.min(elapsedDays, previousPeriod.lengthOfMonth()));

        List<Transaction> current = transactionRepository.findByUserIdAndTransactionDateBetween(user.getId(), currentStart, currentEnd);
        List<Transaction> previous = transactionRepository.findByUserIdAndTransactionDateBetween(user.getId(), previousStart, previousEnd);
        BigDecimal currentIncome = sum(current, TransactionType.INCOME);
        BigDecimal previousIncome = sum(previous, TransactionType.INCOME);
        BigDecimal currentExpense = sum(current, TransactionType.EXPENSE);
        BigDecimal previousExpense = sum(previous, TransactionType.EXPENSE);

        Map<Long, Map<String, Object>> categoryDeltas = new HashMap<>();
        addCategoryAmounts(categoryDeltas, current, "currentAmount");
        addCategoryAmounts(categoryDeltas, previous, "previousAmount");
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Map<String, Object> item : categoryDeltas.values()) {
            BigDecimal now = (BigDecimal) item.getOrDefault("currentAmount", BigDecimal.ZERO);
            BigDecimal then = (BigDecimal) item.getOrDefault("previousAmount", BigDecimal.ZERO);
            item.put("currentAmount", now);
            item.put("previousAmount", then);
            item.put("changeAmount", now.subtract(then));
            item.put("changePercent", percentChange(now, then));
            categories.add(item);
        }
        categories.sort(Comparator.comparing(item -> (BigDecimal) item.get("currentAmount"), Comparator.reverseOrder()));

        Map<String, Object> response = new HashMap<>();
        response.put("currentStart", currentStart);
        response.put("currentEnd", currentEnd);
        response.put("previousStart", previousStart);
        response.put("previousEnd", previousEnd);
        response.put("currentIncome", currentIncome);
        response.put("previousIncome", previousIncome);
        response.put("currentExpense", currentExpense);
        response.put("previousExpense", previousExpense);
        response.put("expenseChangeAmount", currentExpense.subtract(previousExpense));
        response.put("expenseChangePercent", percentChange(currentExpense, previousExpense));
        response.put("categories", categories);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/budget-plan")
    public ResponseEntity<Map<String, Object>> getBudgetPlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year
    ) {
        YearMonth period = month != null && year != null ? YearMonth.of(year, month) : YearMonth.now();
        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateBetween(
                user.getId(), period.atDay(1), period.atEndOfMonth());
        BigDecimal income = sum(transactions, TransactionType.INCOME);
        BigDecimal needs = BigDecimal.ZERO;
        BigDecimal wants = BigDecimal.ZERO;
        BigDecimal unclassified = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) continue;
            expense = expense.add(tx.getAmount());
            BudgetGroup group = tx.getCategory().getBudgetGroup();
            if (group == BudgetGroup.NEEDS) needs = needs.add(tx.getAmount());
            else if (group == BudgetGroup.WANTS) wants = wants.add(tx.getAmount());
            else unclassified = unclassified.add(tx.getAmount());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("month", period.getMonthValue());
        response.put("year", period.getYear());
        response.put("income", income);
        response.put("expense", expense);
        response.put("needs", bucket(needs, income.multiply(new BigDecimal("0.50"))));
        response.put("wants", bucket(wants, income.multiply(new BigDecimal("0.30"))));
        response.put("savings", bucket(income.subtract(expense), income.multiply(new BigDecimal("0.20"))));
        response.put("unclassifiedExpense", unclassified);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/savings-forecast")
    public ResponseEntity<Map<String, Object>> getSavingsForecast(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "6") int months
    ) {
        int horizon = Math.max(1, Math.min(months, 12));
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(5);
        List<Transaction> recent = transactionRepository.findByUserIdAndTransactionDateBetween(
                user.getId(), first.atDay(1), current.atEndOfMonth());
        BigDecimal income = sum(recent, TransactionType.INCOME);
        BigDecimal expense = sum(recent, TransactionType.EXPENSE);
        BigDecimal averageMonthlyNet = income.subtract(expense).divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);

        Map<String, Object> response = new HashMap<>();
        response.put("basedOnMonths", 6);
        response.put("averageMonthlyIncome", income.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP));
        response.put("averageMonthlyExpense", expense.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP));
        response.put("averageMonthlyNet", averageMonthlyNet);
        response.put("horizonMonths", horizon);
        response.put("estimatedAdditionalSavings", averageMonthlyNet.multiply(BigDecimal.valueOf(horizon)));
        response.put("disclaimer", "Estimate based on the last six calendar months; it is not guaranteed financial advice.");
        return ResponseEntity.ok(response);
    }

    private BigDecimal sum(List<Transaction> transactions, TransactionType type) {
        return transactions.stream().filter(tx -> tx.getType() == type)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addCategoryAmounts(Map<Long, Map<String, Object>> totals, List<Transaction> transactions, String amountKey) {
        for (Transaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) continue;
            Map<String, Object> item = totals.computeIfAbsent(tx.getCategory().getId(), id -> {
                Map<String, Object> value = new HashMap<>();
                value.put("categoryId", id);
                value.put("categoryName", tx.getCategory().getName());
                value.put("categoryColor", tx.getCategory().getDisplayColor());
                return value;
            });
            item.put(amountKey, ((BigDecimal) item.getOrDefault(amountKey, BigDecimal.ZERO)).add(tx.getAmount()));
        }
    }

    private double percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return current.compareTo(BigDecimal.ZERO) == 0 ? 0 : 100;
        return current.subtract(previous).multiply(new BigDecimal("100"))
                .divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private Map<String, Object> bucket(BigDecimal actual, BigDecimal target) {
        Map<String, Object> response = new HashMap<>();
        response.put("actual", actual);
        response.put("target", target);
        response.put("remaining", target.subtract(actual));
        response.put("percentOfIncome", target.signum() == 0 ? 0 : actual.multiply(new BigDecimal("100")).divide(target, 1, RoundingMode.HALF_UP).doubleValue());
        return response;
    }
}
