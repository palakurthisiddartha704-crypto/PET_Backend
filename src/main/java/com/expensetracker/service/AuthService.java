package com.expensetracker.service;

import com.expensetracker.dto.AuthDtos.*;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.BudgetGroup;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .build();

        User savedUser = userRepository.save(user);

        // Seed default categories
        seedDefaultCategories(savedUser);

        String token = jwtTokenProvider.generateToken(savedUser.getEmail(), savedUser.getId());

        return AuthResponse.builder()
                .token(token)
                .user(UserDto.builder()
                        .id(savedUser.getId())
                        .email(savedUser.getEmail())
                        .fullName(savedUser.getFullName())
                        .currency(savedUser.getCurrency())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Invalid email or password credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId());

        return AuthResponse.builder()
                .token(token)
                .user(UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .currency(user.getCurrency())
                        .build())
                .build();
    }

    private void seedDefaultCategories(User user) {
        List<Category> defaults = List.of(
                Category.builder().user(user).name("Food & Dining").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#ef4444").icon("Utensils").isDefault(true).build(),
                Category.builder().user(user).name("Transportation").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#f97316").icon("Car").isDefault(true).build(),
                Category.builder().user(user).name("Shopping").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.WANTS).displayColor("#ec4899").icon("ShoppingBag").isDefault(true).build(),
                Category.builder().user(user).name("Rent & Housing").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#8b5cf6").icon("Home").isDefault(true).build(),
                Category.builder().user(user).name("Utilities").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#3b82f6").icon("Zap").isDefault(true).build(),
                Category.builder().user(user).name("Healthcare").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#06b6d4").icon("Activity").isDefault(true).build(),
                Category.builder().user(user).name("Education").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.NEEDS).displayColor("#10b981").icon("GraduationCap").isDefault(true).build(),
                Category.builder().user(user).name("Entertainment").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.WANTS).displayColor("#f59e0b").icon("Film").isDefault(true).build(),
                Category.builder().user(user).name("Travel").type(TransactionType.EXPENSE).budgetGroup(BudgetGroup.WANTS).displayColor("#6366f1").icon("Plane").isDefault(true).build(),
                Category.builder().user(user).name("Other Expenses").type(TransactionType.EXPENSE).displayColor("#64748b").icon("MoreHorizontal").isDefault(true).build(),

                Category.builder().user(user).name("Salary").type(TransactionType.INCOME).displayColor("#10b981").icon("Briefcase").isDefault(true).build(),
                Category.builder().user(user).name("Freelance").type(TransactionType.INCOME).displayColor("#059669").icon("Laptop").isDefault(true).build(),
                Category.builder().user(user).name("Business").type(TransactionType.INCOME).displayColor("#047857").icon("TrendingUp").isDefault(true).build(),
                Category.builder().user(user).name("Investments").type(TransactionType.INCOME).displayColor("#0284c7").icon("BarChart2").isDefault(true).build(),
                Category.builder().user(user).name("Gifts & Awards").type(TransactionType.INCOME).displayColor("#8b5cf6").icon("Gift").isDefault(true).build(),
                Category.builder().user(user).name("Other Income").type(TransactionType.INCOME).displayColor("#64748b").icon("PlusCircle").isDefault(true).build()
        );
        categoryRepository.saveAll(defaults);
    }
}
