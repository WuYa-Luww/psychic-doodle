package com.lww.medication.repository;

import com.lww.medication.entity.MedicationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 服药记录 Repository
 */
@Repository
public interface MedicationRecordRepository extends JpaRepository<MedicationRecord, Long> {

    List<MedicationRecord> findByUserId(Long userId);

    List<MedicationRecord> findByUserIdAndStatus(Long userId, String status);

    List<MedicationRecord> findByUserIdAndScheduledTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<MedicationRecord> findByReminderIdAndScheduledTime(Long reminderId, LocalDateTime scheduledTime);

    /**
     * 查询用户在指定时间范围内待服药的记录
     */
    @Query("SELECT r FROM MedicationRecord r WHERE r.userId = :userId AND r.status = 'PENDING' AND r.scheduledTime BETWEEN :start AND :end")
    List<MedicationRecord> findPendingByUserIdAndTimeRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 统计用户在指定时间范围内的服药记录
     */
    @Query("SELECT COUNT(r) FROM MedicationRecord r WHERE r.userId = :userId AND r.scheduledTime BETWEEN :start AND :end")
    Long countByUserIdAndTimeRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 统计用户在指定时间范围内已服药的记录
     */
    @Query("SELECT COUNT(r) FROM MedicationRecord r WHERE r.userId = :userId AND r.status = 'TAKEN' AND r.scheduledTime BETWEEN :start AND :end")
    Long countTakenByUserIdAndTimeRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
