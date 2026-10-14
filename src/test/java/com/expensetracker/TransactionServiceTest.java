package com.expensetracker;

import com.expensetracker.dto.TransactionDtos.TransactionRequest;
import com.expensetracker.dto.TransactionDtos.TransactionResponse;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Category expenseCategory;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("alex@example.com").fullName("Alex").build();
        expenseCategory = Category.builder().id(10L).user(user).name("Groceries").type(TransactionType.EXPENSE).displayColor("#ef4444").build();
    }

    @Test
    @DisplayName("Successfully create a valid expense transaction")
    void shouldCreateTransactionSuccessfully() {
        TransactionRequest request = TransactionRequest.builder()
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("150.50"))
                .title("Supermarket trip")
                .categoryId(10L)
                .transactionDate(LocalDate.now())
                .paymentMethod("Credit Card")
                .notes("Weekly food")
                .build();

        when(categoryRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(expenseCategory));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(new BigDecimal("150.50"), response.getAmount());
        assertEquals("Supermarket trip", response.getTitle());
        assertEquals("Groceries", response.getCategoryName());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Reject transaction with invalid zero or negative amount")
    void shouldThrowWhenAmountIsZeroOrNegative() {
        TransactionRequest request = TransactionRequest.builder()
                .type(TransactionType.EXPENSE)
                .amount(BigDecimal.ZERO)
                .title("Invalid zero")
                .categoryId(10L)
                .transactionDate(LocalDate.now())
                .build();

        assertThrows(BadRequestException.class, () -> transactionService.createTransaction(1L, request));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Prevent assigning expense transaction to an income category")
    void shouldThrowWhenCategoryTypeMismatches() {
        Category incomeCategory = Category.builder().id(20L).user(user).name("Salary").type(TransactionType.INCOME).build();

        TransactionRequest request = TransactionRequest.builder()
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100.00"))
                .title("Should fail")
                .categoryId(20L)
                .transactionDate(LocalDate.now())
                .build();

        when(categoryRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(incomeCategory));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> transactionService.createTransaction(1L, request));
        assertTrue(ex.getMessage().contains("Cannot assign a EXPENSE transaction to INCOME category"));
    }
}
