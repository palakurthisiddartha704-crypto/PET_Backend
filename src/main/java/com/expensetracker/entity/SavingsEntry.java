package com.expensetracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_entries", indexes = {
        @Index(name = "idx_savings_user_created", columnList = "user_id, created_at")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_savings_rollover_key",
        columnNames = {"user_id", "rollover_key"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 24)
    private SavingsEntryType entryType;

    /** Positive for deposits and monthly rollovers; negative for withdrawals. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "period_year")
    private Integer periodYear;

    @Column(name = "period_month")
    private Integer periodMonth;

        @Column(name = "rollover_key", length = 32)
        private String rolloverKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
