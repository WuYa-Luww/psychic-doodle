package com.lww.medication.repository;

import com.lww.medication.entity.MedicationReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用药提醒 Repository
 */
@Repository
public interface MedicationReminderRepository extends JpaRepository<MedicationReminder, Long> {

    List<MedicationReminder> findByUserIdAndEnabledTrue(Long userId);

    List<MedicationReminder> findByUserId(Long userId);

    List<MedicationReminder> findByEnabledTrue();
}
