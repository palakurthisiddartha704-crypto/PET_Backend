package com.expensetracker.service;

import com.expensetracker.dto.RecurringDtos.*;
import com.expensetracker.entity.*;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.RecurringTransactionRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RecurringResponse> getRecurringRules(Long userId) {
        return recurringRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecurringResponse getRecurringRuleById(Long userId, Long ruleId) {
        RecurringTransaction rule = recurringRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found with ID: " + ruleId));
        return mapToResponse(rule);
    }

    @Transactional
    public RecurringResponse createRecurringRule(Long userId, RecurringRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Recurring amount must be greater than zero");
        }

        Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                .orElseThrow(() -> new BadRequestException("Category not found or does not belong to user"));

        if (category.getType() != request.getType()) {
            throw new BadRequestException("Cannot assign " + request.getType() + " rule to " + category.getType() + " category");
        }

        User user = userRepository.getReferenceById(userId);

        LocalDate nextDate = request.getNextExecutionDate() != null
                ? request.getNextExecutionDate()
                : request.getStartDate();

        RecurringTransaction rule = RecurringTransaction.builder()
                .user(user)
                .category(category)
                .title(request.getTitle().trim())
                .type(request.getType())
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .nextExecutionDate(nextDate)
                .active(request.getActive() != null ? request.getActive() : true)
                .subscription(request.getSubscription() != null ? request.getSubscription() : false)
                .build();

        return mapToResponse(recurringRepository.save(rule));
    }

    @Transactional
    public RecurringResponse updateRecurringRule(Long userId, Long ruleId, RecurringRequest request) {
        RecurringTransaction rule = recurringRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found with ID: " + ruleId));

        if (request.getTitle() != null) rule.setTitle(request.getTitle().trim());
        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Amount must be greater than zero");
            }
            rule.setAmount(request.getAmount());
        }
        if (request.getFrequency() != null) rule.setFrequency(request.getFrequency());
        if (request.getStartDate() != null) rule.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) rule.setEndDate(request.getEndDate());
        if (request.getNextExecutionDate() != null) rule.setNextExecutionDate(request.getNextExecutionDate());
        if (request.getActive() != null) rule.setActive(request.getActive());
        if (request.getSubscription() != null) rule.setSubscription(request.getSubscription());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                    .orElseThrow(() -> new BadRequestException("Category not found"));
            rule.setCategory(category);
        }

        return mapToResponse(recurringRepository.save(rule));
    }

    @Transactional
    public RecurringResponse toggleStatus(Long userId, Long ruleId, Boolean active) {
        RecurringTransaction rule = recurringRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found with ID: " + ruleId));

        rule.setActive(active != null ? active : !rule.getActive());
        return mapToResponse(recurringRepository.save(rule));
    }

    @Transactional
    public void deleteRecurringRule(Long userId, Long ruleId) {
        RecurringTransaction rule = recurringRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found with ID: " + ruleId));
        recurringRepository.delete(rule);
    }

    /**
     * Scheduled Job: Runs every day at midnight to process due recurring transactions.
     * Prevents duplicate transactions by checking if a transaction was already generated for this rule and date.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void processDueRecurringTransactions() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueRules = recurringRepository.findByActiveTrueAndNextExecutionDateLessThanEqual(today);

        log.info("Processing {} due recurring transactions on {}", dueRules.size(), today);

        for (RecurringTransaction rule : dueRules) {
            int catchUpLimit = 240;
            while (Boolean.TRUE.equals(rule.getActive()) && !rule.getNextExecutionDate().isAfter(today) && catchUpLimit-- > 0) {
                processRuleExecution(rule, today);
            }
            if (catchUpLimit <= 0) {
                log.warn("Stopped recurrence catch-up at 240 occurrences for rule {}", rule.getId());
            }
        }
    }

    @Transactional
    public void processRuleExecution(RecurringTransaction rule, LocalDate today) {
        if (rule.getEndDate() != null && rule.getNextExecutionDate().isAfter(rule.getEndDate())) {
            rule.setActive(false);
            recurringRepository.save(rule);
            return;
        }

        LocalDate occurrenceDate = rule.getNextExecutionDate();
        if (!transactionRepository.existsByRecurringRuleIdAndRecurringOccurrenceDate(rule.getId(), occurrenceDate)) {
            Transaction tx = Transaction.builder()
                .user(rule.getUser())
                .category(rule.getCategory())
                .recurringRule(rule)
                .recurringOccurrenceDate(occurrenceDate)
                .type(rule.getType())
                .amount(rule.getAmount())
                .title(rule.getTitle())
                .transactionDate(occurrenceDate)
                .paymentMethod("Auto-Debit")
                .notes("Automatically generated from recurring rule #" + rule.getId())
                .build();
            transactionRepository.save(tx);
        }

        rule.setLastExecutedAt(occurrenceDate);

        // Calculate next execution date
        LocalDate nextDate = computeNextDate(occurrenceDate, rule.getFrequency());
        rule.setNextExecutionDate(nextDate);

        if (rule.getEndDate() != null && nextDate.isAfter(rule.getEndDate())) {
            rule.setActive(false);
        }

        recurringRepository.save(rule);
    }

    private LocalDate computeNextDate(LocalDate current, Frequency frequency) {
        return switch (frequency) {
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }

    private RecurringResponse mapToResponse(RecurringTransaction rule) {
        return RecurringResponse.builder()
                .id(rule.getId())
                .title(rule.getTitle())
                .type(rule.getType())
                .amount(rule.getAmount())
                .categoryId(rule.getCategory().getId())
                .categoryName(rule.getCategory().getName())
                .categoryColor(rule.getCategory().getDisplayColor())
                .categoryIcon(rule.getCategory().getIcon())
                .frequency(rule.getFrequency())
                .startDate(rule.getStartDate())
                .endDate(rule.getEndDate())
                .nextExecutionDate(rule.getNextExecutionDate())
                .active(rule.getActive())
                .subscription(rule.getSubscription())
                .lastExecutedAt(rule.getLastExecutedAt())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
