package com.expensetracker.service;

import com.expensetracker.dto.CategoryDtos.*;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(Long userId, TransactionType type) {
        List<Category> list = (type != null)
                ? categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type)
                : categoryRepository.findByUserIdOrderByNameAsc(userId);

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long userId, Long categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(userId, request.getName().trim(), request.getType())) {
            throw new BadRequestException("Category '" + request.getName() + "' already exists for " + request.getType());
        }

        User user = userRepository.getReferenceById(userId);

        Category category = Category.builder()
                .user(user)
                .name(request.getName().trim())
                .type(request.getType())
                .description(request.getDescription())
                .displayColor(request.getDisplayColor() != null ? request.getDisplayColor() : "#3b82f6")
                .icon(request.getIcon() != null ? request.getIcon() : "Tag")
                .budgetGroup(request.getType() == TransactionType.EXPENSE ? request.getBudgetGroup() : null)
                .isDefault(false)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        if (request.getName() != null && !request.getName().trim().equalsIgnoreCase(category.getName())) {
            if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(userId, request.getName().trim(), category.getType())) {
                throw new BadRequestException("Category '" + request.getName() + "' already exists for " + category.getType());
            }
            category.setName(request.getName().trim());
        }

        if (request.getDescription() != null) category.setDescription(request.getDescription().trim());
        if (request.getDisplayColor() != null) category.setDisplayColor(request.getDisplayColor());
        if (request.getIcon() != null) category.setIcon(request.getIcon());
        if (category.getType() == TransactionType.EXPENSE) category.setBudgetGroup(request.getBudgetGroup());

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // Enforce Section 6 rule: Prevent deletion if transactions depend on it
        long count = transactionRepository.countByUserIdAndCategoryId(userId, categoryId);
        if (count > 0) {
            throw new BadRequestException("Cannot delete category '" + category.getName() + "' because " + count + " transaction(s) are associated with it.");
        }

        budgetRepository.deleteByCategoryIdAndUserId(categoryId, userId);
        categoryRepository.delete(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .description(category.getDescription())
                .displayColor(category.getDisplayColor())
                .icon(category.getIcon())
                .budgetGroup(category.getBudgetGroup())
                .isDefault(category.getIsDefault())
                .build();
    }
}
