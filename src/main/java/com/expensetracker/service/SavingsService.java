package com.expensetracker.service;

import com.expensetracker.entity.*;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.repository.SavingsEntryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavingsService {

    private final SavingsEntryRepository savingsRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> getOverview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User account not found"));
        closeCompletedMonths(user);

        YearMonth currentMonth = YearMonth.now();
        BigDecimal income = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, currentMonth.atDay(1), currentMonth.atEndOfMonth());
        BigDecimal expenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, currentMonth.atDay(1), currentMonth.atEndOfMonth());
        BigDecimal depositedThisMonth = savingsRepository.findByUserIdAndEntryTypeAndPeriodYearAndPeriodMonth(
                        userId, SavingsEntryType.MANUAL_DEPOSIT, currentMonth.getYear(), currentMonth.getMonthValue())
                .stream().map(SavingsEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableToSave = income.subtract(expenses).subtract(depositedThisMonth).max(BigDecimal.ZERO);

        Map<String, Object> result = new HashMap<>();
        result.put("balance", savingsRepository.sumBalanceByUserId(userId));
        result.put("availableToSave", availableToSave);
        result.put("monthIncome", income);
        result.put("monthExpenses", expenses);
        result.put("currentMonth", currentMonth.getMonthValue());
        result.put("currentYear", currentMonth.getYear());
        result.put("entries", savingsRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(entry -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", entry.getId());
            row.put("type", entry.getEntryType());
            row.put("amount", entry.getAmount());
            row.put("periodMonth", entry.getPeriodMonth());
            row.put("periodYear", entry.getPeriodYear());
            row.put("createdAt", entry.getCreatedAt());
            return row;
        }).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> deposit(Long userId, BigDecimal requestedAmount) {
        BigDecimal amount = validateAmount(requestedAmount);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User account not found"));
        closeCompletedMonths(user);

        YearMonth period = YearMonth.now();
        BigDecimal income = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, period.atDay(1), period.atEndOfMonth());
        BigDecimal expenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, period.atDay(1), period.atEndOfMonth());
        BigDecimal alreadyDeposited = savingsRepository.findByUserIdAndEntryTypeAndPeriodYearAndPeriodMonth(
                        userId, SavingsEntryType.MANUAL_DEPOSIT, period.getYear(), period.getMonthValue())
                .stream().map(SavingsEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = income.subtract(expenses).subtract(alreadyDeposited).max(BigDecimal.ZERO);
        if (amount.compareTo(available) > 0) {
            throw new BadRequestException("Amount exceeds this month's available balance of " + available.setScale(2, RoundingMode.HALF_UP));
        }

        savingsRepository.save(SavingsEntry.builder()
                .user(user)
                .entryType(SavingsEntryType.MANUAL_DEPOSIT)
                .amount(amount)
                .periodYear(period.getYear())
                .periodMonth(period.getMonthValue())
                .build());
        return getOverview(userId);
    }

    @Transactional
    public Map<String, Object> withdraw(Long userId, BigDecimal requestedAmount) {
        BigDecimal amount = validateAmount(requestedAmount);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User account not found"));
        closeCompletedMonths(user);
        BigDecimal balance = savingsRepository.sumBalanceByUserId(userId);
        if (amount.compareTo(balance) > 0) {
            throw new BadRequestException("Withdrawal exceeds your overall savings balance of " + balance.setScale(2, RoundingMode.HALF_UP));
        }
        savingsRepository.save(SavingsEntry.builder()
                .user(user)
                .entryType(SavingsEntryType.WITHDRAWAL)
                .amount(amount.negate())
                .periodYear(YearMonth.now().getYear())
                .periodMonth(YearMonth.now().getMonthValue())
                .build());
        return getOverview(userId);
    }

    @Transactional
    public void closeCompletedMonths(User user) {
        YearMonth month = YearMonth.from(user.getCreatedAt().toLocalDate()).plusMonths(0);
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        int safetyLimit = 240;
        while (!month.isAfter(previousMonth) && safetyLimit-- > 0) {
            closeMonth(user, month);
            month = month.plusMonths(1);
        }
    }

    @Scheduled(cron = "0 5 0 1 * ?")
    @Transactional
    public void closePreviousMonthForAllUsers() {
        YearMonth completedMonth = YearMonth.now().minusMonths(1);
        List<User> users = userRepository.findAll();
        for (User user : users) closeMonth(user, completedMonth);
    }

    private void closeMonth(User user, YearMonth period) {
        String rolloverKey = period.getYear() + "-" + String.format("%02d", period.getMonthValue());
        if (savingsRepository.existsByUserIdAndRolloverKey(user.getId(), rolloverKey)) return;

        BigDecimal income = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                user.getId(), TransactionType.INCOME, period.atDay(1), period.atEndOfMonth());
        BigDecimal expenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                user.getId(), TransactionType.EXPENSE, period.atDay(1), period.atEndOfMonth());
        BigDecimal manuallyDeposited = savingsRepository.findByUserIdAndEntryTypeAndPeriodYearAndPeriodMonth(
                        user.getId(), SavingsEntryType.MANUAL_DEPOSIT, period.getYear(), period.getMonthValue())
                .stream().map(SavingsEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal leftover = income.subtract(expenses).subtract(manuallyDeposited);
        if (leftover.signum() <= 0) return;

        savingsRepository.save(SavingsEntry.builder()
                .user(user)
                .entryType(SavingsEntryType.MONTHLY_ROLLOVER)
                .amount(leftover.setScale(2, RoundingMode.HALF_UP))
                .periodYear(period.getYear())
                .periodMonth(period.getMonthValue())
                .rolloverKey(rolloverKey)
                .build());
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Savings amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
