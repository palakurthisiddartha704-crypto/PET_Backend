package com.expensetracker.service;

import com.expensetracker.dto.TransactionDtos.*;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            Long userId,
            TransactionType type,
            Long categoryId,
            String paymentMethod,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            Pageable pageable
    ) {
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (paymentMethod != null && !paymentMethod.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("paymentMethod")), paymentMethod.toLowerCase()));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase().trim() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchPattern);
                Predicate notesMatch = cb.like(cb.lower(root.get("notes")), searchPattern);
                predicates.add(cb.or(titleMatch, notesMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long userId, Long transactionId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        return mapToResponse(tx);
    }

    @Transactional
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transaction amount must be strictly greater than zero");
        }

        Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                .orElseThrow(() -> new BadRequestException("Invalid category ID or category does not belong to user"));

        // Enforce rule: expense cannot be assigned to income category
        if (category.getType() != request.getType()) {
            throw new BadRequestException("Cannot assign a " + request.getType() + " transaction to " + category.getType() + " category '" + category.getName() + "'");
        }

        User user = userRepository.getReferenceById(userId);

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .type(request.getType())
                .amount(request.getAmount())
                .title(request.getTitle().trim())
                .transactionDate(request.getTransactionDate())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod().trim() : "Card")
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .build();

        return mapToResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse updateTransaction(Long userId, Long transactionId, TransactionRequest request) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Transaction amount must be strictly greater than zero");
            }
            tx.setAmount(request.getAmount());
        }

        TransactionType targetType = (request.getType() != null) ? request.getType() : tx.getType();

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.getCategoryId(), userId)
                    .orElseThrow(() -> new BadRequestException("Invalid category ID"));

            if (category.getType() != targetType) {
                throw new BadRequestException("Cannot assign a " + targetType + " transaction to " + category.getType() + " category '" + category.getName() + "'");
            }
            tx.setCategory(category);
        }

        if (request.getType() != null) tx.setType(request.getType());
        if (request.getTitle() != null) tx.setTitle(request.getTitle().trim());
        if (request.getTransactionDate() != null) tx.setTransactionDate(request.getTransactionDate());
        if (request.getPaymentMethod() != null) tx.setPaymentMethod(request.getPaymentMethod().trim());
        if (request.getNotes() != null) tx.setNotes(request.getNotes().trim());

        return mapToResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(Long userId, Long transactionId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        transactionRepository.delete(tx);
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .title(tx.getTitle())
                .categoryId(tx.getCategory().getId())
                .categoryName(tx.getCategory().getName())
                .categoryColor(tx.getCategory().getDisplayColor())
                .categoryIcon(tx.getCategory().getIcon())
                .transactionDate(tx.getTransactionDate())
                .paymentMethod(tx.getPaymentMethod())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
