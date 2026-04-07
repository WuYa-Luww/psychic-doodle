package com.lww.medication.service;

import com.lww.medication.dto.RecordResponse;
import com.lww.medication.entity.MedicationRecord;
import com.lww.medication.entity.MedicationReminder;
import com.lww.medication.repository.MedicationRecordRepository;
import com.lww.medication.repository.MedicationReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服药记录服务
 */
@Service
public class MedicationRecordService {

    @Autowired
    private MedicationRecordRepository recordRepository;

    @Autowired
    private MedicationReminderRepository reminderRepository;

    /**
     * 确认服药
     */
    public RecordResponse confirmMedication(Long userId, Long recordId) {
        MedicationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("记录不存在"));

        if (!record.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此记录");
        }

        record.setStatus("TAKEN");
        record.setActualTime(LocalDateTime.now());

        record = recordRepository.save(record);
        return toResponse(record, null);
    }

    /**
     * 跳过服药
     */
    public RecordResponse skipMedication(Long userId, Long recordId, String reason) {
        MedicationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("记录不存在"));

        if (!record.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此记录");
        }

        record.setStatus("SKIPPED");
        record.setNotes(reason);

        record = recordRepository.save(record);
        return toResponse(record, null);
    }

    /**
     * 获取用户待服药记录（轮询接口使用）
     */
    public List<RecordResponse> getPendingRecords(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMinutes(30); // 提前30分钟提醒
        LocalDateTime end = now.plusMinutes(30);    // 延后30分钟

        List<MedicationRecord> records = recordRepository.findPendingByUserIdAndTimeRange(userId, start, end);
        return toResponseList(records);
    }

    /**
     * 获取用户指定日期范围的记录
     */
    public List<RecordResponse> getRecordsByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        List<MedicationRecord> records = recordRepository.findByUserIdAndScheduledTimeBetween(userId, start, end);
        return toResponseList(records);
    }

    /**
     * 获取用户服药统计
     */
    public Map<String, Object> getStats(Long userId, int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        Long total = recordRepository.countByUserIdAndTimeRange(userId, start, end);
        Long taken = recordRepository.countTakenByUserIdAndTimeRange(userId, start, end);

        double adherenceRate = total > 0 ? (double) taken / total * 100 : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("taken", taken);
        stats.put("adherenceRate", Math.round(adherenceRate * 10) / 10.0); // 保留1位小数
        stats.put("period", days + "天");

        return stats;
    }

    /**
     * 创建服药记录（定时任务使用）
     */
    public void createRecord(Long userId, Long reminderId, LocalDateTime scheduledTime) {
        // 检查是否已存在
        if (recordRepository.findByReminderIdAndScheduledTime(reminderId, scheduledTime).isPresent()) {
            return;
        }

        MedicationRecord record = new MedicationRecord();
        record.setUserId(userId);
        record.setReminderId(reminderId);
        record.setScheduledTime(scheduledTime);
        record.setStatus("PENDING");

        recordRepository.save(record);
    }

    /**
     * 标记过期未服记录为 MISSED
     */
    public void markMissedRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2); // 超过2小时未服视为漏服

        List<MedicationRecord> missedRecords = recordRepository.findByUserIdAndStatus(null, "PENDING").stream()
                .filter(r -> r.getScheduledTime().isBefore(threshold))
                .collect(Collectors.toList());

        for (MedicationRecord record : missedRecords) {
            record.setStatus("MISSED");
            recordRepository.save(record);
        }
    }

    /**
     * 转换为响应列表
     */
    private List<RecordResponse> toResponseList(List<MedicationRecord> records) {
        // 获取所有关联的提醒信息
        Map<Long, MedicationReminder> reminderMap = new HashMap<>();
        for (MedicationRecord record : records) {
            if (!reminderMap.containsKey(record.getReminderId())) {
                reminderRepository.findById(record.getReminderId())
                        .ifPresent(r -> reminderMap.put(r.getId(), r));
            }
        }

        return records.stream()
                .map(r -> toResponse(r, reminderMap.get(r.getReminderId())))
                .collect(Collectors.toList());
    }

    /**
     * 转换为响应 DTO
     */
    private RecordResponse toResponse(MedicationRecord record, MedicationReminder reminder) {
        RecordResponse response = new RecordResponse();
        response.setId(record.getId());
        response.setReminderId(record.getReminderId());
        response.setScheduledTime(record.getScheduledTime());
        response.setActualTime(record.getActualTime());
        response.setStatus(record.getStatus());
        response.setNotes(record.getNotes());

        if (reminder != null) {
            response.setMedicationName(reminder.getMedicationName());
            response.setDosage(reminder.getDosage());
        }

        return response;
    }
}
