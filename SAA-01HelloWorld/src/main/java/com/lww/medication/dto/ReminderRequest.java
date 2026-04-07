package com.lww.medication.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 用药提醒请求 DTO
 */
public class ReminderRequest {

    private String medicationName;
    private String dosage;
    private String frequency; // DAILY / WEEKLY
    private List<String> remindTimes; // ["08:00", "20:00"]
    private String daysOfWeek; // "1,3,5" 表示周一三五
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;

    // Getters and Setters
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public List<String> getRemindTimes() { return remindTimes; }
    public void setRemindTimes(List<String> remindTimes) { this.remindTimes = remindTimes; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
