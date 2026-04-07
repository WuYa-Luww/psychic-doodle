package com.lww.medication.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lww.medication.entity.MedicationReminder;
import com.lww.medication.repository.MedicationReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 用药提醒定时任务调度器
 */
@Component
@EnableScheduling
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    @Autowired
    private MedicationReminderRepository reminderRepository;

    @Autowired
    private MedicationRecordService recordService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每分钟扫描，生成待服药记录
     */
    @Scheduled(cron = "0 * * * * ?")
    public void generateMedicationRecords() {
        log.debug("开始扫描用药提醒...");

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        // 获取所有启用的提醒
        List<MedicationReminder> reminders = reminderRepository.findByEnabledTrue();

        for (MedicationReminder reminder : reminders) {
            try {
                processReminder(reminder, today, currentTime);
            } catch (Exception e) {
                log.error("处理提醒失败: id={}, error={}", reminder.getId(), e.getMessage());
            }
        }

        // 标记漏服记录
        recordService.markMissedRecords();
    }

    /**
     * 处理单个提醒
     */
    private void processReminder(MedicationReminder reminder, LocalDate today, LocalTime currentTime) {
        // 检查是否在有效期内
        if (reminder.getStartDate() != null && today.isBefore(reminder.getStartDate())) {
            return;
        }
        if (reminder.getEndDate() != null && today.isAfter(reminder.getEndDate())) {
            return;
        }

        // 检查是否是今天需要服药
        if (!shouldTakeToday(reminder, today)) {
            return;
        }

        // 解析提醒时间
        List<String> times = parseRemindTimes(reminder.getRemindTimes());
        if (times.isEmpty()) {
            return;
        }

        // 检查当前时间是否匹配提醒时间（前后1分钟内）
        for (String timeStr : times) {
            try {
                LocalTime remindTime = LocalTime.parse(timeStr);
                LocalTime timeWindowStart = remindTime.minusMinutes(1);
                LocalTime timeWindowEnd = remindTime.plusMinutes(1);

                if (currentTime.isAfter(timeWindowStart) && currentTime.isBefore(timeWindowEnd)) {
                    // 生成服药记录
                    LocalDateTime scheduledTime = LocalDateTime.of(today, remindTime);
                    recordService.createRecord(reminder.getUserId(), reminder.getId(), scheduledTime);
                    log.info("生成服药记录: userId={}, medication={}, time={}",
                            reminder.getUserId(), reminder.getMedicationName(), scheduledTime);
                }
            } catch (Exception e) {
                log.warn("解析时间失败: {}", timeStr);
            }
        }
    }

    /**
     * 检查今天是否需要服药
     */
    private boolean shouldTakeToday(MedicationReminder reminder, LocalDate today) {
        String frequency = reminder.getFrequency();

        if ("DAILY".equalsIgnoreCase(frequency)) {
            return true;
        }

        if ("WEEKLY".equalsIgnoreCase(frequency)) {
            String daysOfWeek = reminder.getDaysOfWeek();
            if (daysOfWeek == null || daysOfWeek.isEmpty()) {
                return false;
            }

            DayOfWeek todayOfWeek = today.getDayOfWeek();
            int dayValue = todayOfWeek.getValue(); // 1=周一, 7=周日

            // 检查今天是否在指定日期中
            String[] days = daysOfWeek.split(",");
            for (String day : days) {
                try {
                    if (Integer.parseInt(day.trim()) == dayValue) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            return false;
        }

        return false;
    }

    /**
     * 解析提醒时间 JSON
     */
    private List<String> parseRemindTimes(String remindTimesJson) {
        try {
            return objectMapper.readValue(remindTimesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
