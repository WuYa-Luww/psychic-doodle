package com.lww.medication.controller;

import com.lww.medication.dto.RecordResponse;
import com.lww.medication.dto.ReminderRequest;
import com.lww.medication.dto.ReminderResponse;
import com.lww.medication.service.MedicationRecordService;
import com.lww.medication.service.MedicationReminderService;
import com.lww.user.entity.User;
import com.lww.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用药管理控制器
 */
@RestController
@RequestMapping("/api/medication")
public class MedicationController {

    @Autowired
    private MedicationReminderService reminderService;

    @Autowired
    private MedicationRecordService recordService;

    @Autowired
    private UserService userService;

    // ==================== 提醒计划管理 ====================

    /**
     * 获取当前用户的提醒计划列表
     */
    @GetMapping("/reminders")
    public ResponseEntity<?> getReminders() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<ReminderResponse> reminders = reminderService.getUserReminders(user.getId());
        return ResponseEntity.ok(reminders);
    }

    /**
     * 创建提醒计划
     */
    @PostMapping("/reminders")
    public ResponseEntity<?> createReminder(@RequestBody ReminderRequest request) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            ReminderResponse response = reminderService.createReminder(user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新提醒计划
     */
    @PutMapping("/reminders/{id}")
    public ResponseEntity<?> updateReminder(@PathVariable Long id, @RequestBody ReminderRequest request) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            ReminderResponse response = reminderService.updateReminder(user.getId(), id, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除提醒计划
     */
    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<?> deleteReminder(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            reminderService.deleteReminder(user.getId(), id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 启用/禁用提醒
     */
    @PatchMapping("/reminders/{id}/toggle")
    public ResponseEntity<?> toggleReminder(@PathVariable Long id, @RequestParam Boolean enabled) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            ReminderResponse response = reminderService.toggleReminder(user.getId(), id, enabled);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 服药记录管理 ====================

    /**
     * 获取待服药记录（轮询接口）
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRecords() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<RecordResponse> records = recordService.getPendingRecords(user.getId());
        return ResponseEntity.ok(records);
    }

    /**
     * 确认服药
     */
    @PostMapping("/records/{id}/confirm")
    public ResponseEntity<?> confirmMedication(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            RecordResponse response = recordService.confirmMedication(user.getId(), id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 跳过服药
     */
    @PostMapping("/records/{id}/skip")
    public ResponseEntity<?> skipMedication(@PathVariable Long id, @RequestParam(required = false) String reason) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            RecordResponse response = recordService.skipMedication(user.getId(), id, reason);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取日历数据
     */
    @GetMapping("/calendar")
    public ResponseEntity<?> getCalendarData(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        LocalDateTime startDate = start != null ? LocalDateTime.parse(start) : LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = end != null ? LocalDateTime.parse(end) : LocalDateTime.now().plusDays(7);

        List<RecordResponse> records = recordService.getRecordsByDateRange(user.getId(), startDate, endDate);
        return ResponseEntity.ok(records);
    }

    /**
     * 获取服药统计
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam(defaultValue = "7") int days) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        Map<String, Object> stats = recordService.getStats(user.getId(), days);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        return userService.getCurrentUser();
    }
}
