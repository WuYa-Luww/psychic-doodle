package com.lww.config;

import com.lww.mcp.McpToolService;
import com.lww.medical.tools.MedicalTools;
import com.lww.medication.service.MedicationReminderService;
import com.lww.service.RagService;
import com.lww.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 医疗工具配置
 */
@Configuration
public class MedicalToolsConfig {

    @Bean
    public MedicalTools medicalTools(RagService ragService,
                                      McpToolService mcpToolService,
                                      MedicationReminderService reminderService,
                                      UserRepository userRepository) {
        return new MedicalTools(ragService, mcpToolService, reminderService, userRepository);
    }
}
