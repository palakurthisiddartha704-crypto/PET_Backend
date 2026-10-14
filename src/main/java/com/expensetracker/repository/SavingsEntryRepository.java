package com.expensetracker.repository;

import com.expensetracker.entity.SavingsEntry;
import com.expensetracker.entity.SavingsEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SavingsEntryRepository extends JpaRepository<SavingsEntry, Long> {
    boolean existsByUserIdAndRolloverKey(Long userId, String rolloverKey);
    List<SavingsEntry> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<SavingsEntry> findByUserIdAndEntryTypeAndPeriodYearAndPeriodMonth(
            Long userId, SavingsEntryType entryType, Integer periodYear, Integer periodMonth);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM SavingsEntry e WHERE e.user.id = :userId")
    BigDecimal sumBalanceByUserId(@Param("userId") Long userId);
}
