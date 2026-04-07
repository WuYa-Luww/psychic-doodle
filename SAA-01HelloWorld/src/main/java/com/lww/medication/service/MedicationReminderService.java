package com.lww.medication.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lww.medication.dto.ReminderRequest;
import com.lww.medication.dto.ReminderResponse;
import com.lww.medication.entity.MedicationReminder;
import com.lww.medication.repository.MedicationReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用药提醒服务
 */
@Service
public class    MedicationReminderService {

    @Autowired
    private MedicationReminderRepository reminderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建用药提醒
     */
    public ReminderResponse createReminder(Long userId, ReminderRequest request) {
        MedicationReminder reminder = new MedicationReminder();
        reminder.setUserId(userId);
        reminder.setMedicationName(request.getMedicationName());
        reminder.setDosage(request.getDosage());
        reminder.setFrequency(request.getFrequency());
        reminder.setDaysOfWeek(request.getDaysOfWeek());
        reminder.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        reminder.setEndDate(request.getEndDate());
        reminder.setNotes(request.getNotes());
        reminder.setEnabled(true);

        // 转换提醒时间列表为 JSON
        try {
            reminder.setRemindTimes(objectMapper.writeValueAsString(request.getRemindTimes()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("提醒时间格式错误");
        }

        reminder = reminderRepository.save(reminder);
        return toResponse(reminder);
    }

    /**
     * 更新用药提醒
     */
    public ReminderResponse updateReminder(Long userId, Long reminderId, ReminderRequest request) {
        MedicationReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("提醒计划不存在"));

        if (!reminder.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此提醒计划");
        }

        reminder.setMedicationName(request.getMedicationName());
        reminder.setDosage(request.getDosage());
        reminder.setFrequency(request.getFrequency());
        reminder.setDaysOfWeek(request.getDaysOfWeek());
        if (request.getStartDate() != null) {
            reminder.setStartDate(request.getStartDate());
        }
        reminder.setEndDate(request.getEndDate());
        reminder.setNotes(request.getNotes());

        try {
            reminder.setRemindTimes(objectMapper.writeValueAsString(request.getRemindTimes()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("提醒时间格式错误");
        }

        reminder = reminderRepository.save(reminder);
        return toResponse(reminder);
    }

    /**
     * 删除用药提醒
     */
    public void deleteReminder(Long userId, Long reminderId) {
        MedicationReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("提醒计划不存在"));

        if (!reminder.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此提醒计划");
        }

        reminderRepository.delete(reminder);
    }

    /**
     * 获取用户的所有提醒计划
     */
    public List<ReminderResponse> getUserReminders(Long userId) {
        return reminderRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户启用的提醒计划
     */
    public List<ReminderResponse> getUserEnabledReminders(Long userId) {
        return reminderRepository.findByUserIdAndEnabledTrue(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 启用/禁用提醒
     */
    public ReminderResponse toggleReminder(Long userId, Long reminderId, Boolean enabled) {
        MedicationReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("提醒计划不存在"));

        if (!reminder.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此提醒计划");
        }

        reminder.setEnabled(enabled);
        reminder = reminderRepository.save(reminder);
        return toResponse(reminder);
    }

    /**
     * 获取所有启用的提醒（定时任务使用）
     */
    public List<MedicationReminder> getAllEnabledReminders() {
        return reminderRepository.findByEnabledTrue();
    }

    /**
     * 转换为响应 DTO
     */
    private ReminderResponse toResponse(MedicationReminder reminder) {
        ReminderResponse response = new ReminderResponse();
        response.setId(reminder.getId());
        response.setMedicationName(reminder.getMedicationName());
        response.setDosage(reminder.getDosage());
        response.setFrequency(reminder.getFrequency());
        response.setDaysOfWeek(reminder.getDaysOfWeek());
        response.setStartDate(reminder.getStartDate());
        response.setEndDate(reminder.getEndDate());
        response.setEnabled(reminder.getEnabled());
        response.setNotes(reminder.getNotes());
        response.setCreatedAt(reminder.getCreatedAt());

        // 解析 JSON 时间列表
        try {
            List<String> times = objectMapper.readValue(reminder.getRemindTimes(), new TypeReference<List<String>>() {});
            response.setRemindTimes(times);
        } catch (JsonProcessingException e) {
            response.setRemindTimes(List.of());
        }

        return response;
    }
}
