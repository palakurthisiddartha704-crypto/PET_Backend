package com.expensetracker.controller;

import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.security.CustomUserDetails;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final TransactionRepository transactionRepository;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Long userId = userDetails.getId();
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> categorySpend = new HashMap<>();
        Set<LocalDate> distinctDays = new HashSet<>();

        for (Transaction t : transactions) {
            distinctDays.add(t.getTransactionDate());
            if (t.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
                String catName = t.getCategory().getName();
                categorySpend.put(catName, categorySpend.getOrDefault(catName, BigDecimal.ZERO).add(t.getAmount()));
            }
        }

        BigDecimal netSavings = totalIncome.subtract(totalExpense);
        int dayCount = Math.max(1, distinctDays.size());
        BigDecimal avgDailyExpense = totalExpense.divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP);

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", start);
        response.put("endDate", end);
        response.put("totalIncome", totalIncome);
        response.put("totalExpense", totalExpense);
        response.put("netSavings", netSavings);
        response.put("avgDailyExpense", avgDailyExpense);
        response.put("activeDays", dayCount);
        response.put("transactionCount", transactions.size());
        response.put("categoryBreakdown", categorySpend);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/export")
    public void exportTransactionsToCsv(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            HttpServletResponse response
    ) throws IOException {

        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userDetails.getId()));

            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (paymentMethod != null && !paymentMethod.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("paymentMethod")), paymentMethod.toLowerCase()));
            }
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern), cb.like(cb.lower(root.get("notes")), pattern)));
            }

            query.orderBy(cb.desc(root.get("transactionDate")), cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Transaction> list = transactionRepository.findAll(spec);

        String filename = "transactions_" + LocalDate.now() + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        try (PrintWriter writer = response.getWriter()) {
            // Write CSV BOM for Excel compatibility
            writer.write('\ufeff');
            writer.println("Transaction ID,Date,Type,Title,Category,Amount,Payment Method,Notes,Created At");

            for (Transaction tx : list) {
                writer.println(String.join(",",
                        escapeCsv(tx.getId()),
                        escapeCsv(tx.getTransactionDate()),
                        escapeCsv(tx.getType()),
                        escapeCsv(tx.getTitle()),
                        escapeCsv(tx.getCategory().getName()),
                        escapeCsv(tx.getAmount().setScale(2, RoundingMode.HALF_UP)),
                        escapeCsv(tx.getPaymentMethod()),
                        escapeCsv(tx.getNotes() != null ? tx.getNotes() : ""),
                        escapeCsv(tx.getCreatedAt())
                ));
            }
        }
    }

    private String escapeCsv(Object val) {
        if (val == null) return "\"\"";
        String str = String.valueOf(val);
        return "\"" + str.replace("\"", "\"\"") + "\"";
    }
}
