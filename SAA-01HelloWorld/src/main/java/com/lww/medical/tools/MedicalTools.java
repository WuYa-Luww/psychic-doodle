package com.lww.medical.tools;

import com.lww.mcp.McpToolService;
import com.lww.medication.dto.ReminderRequest;
import com.lww.medication.dto.ReminderResponse;
import com.lww.medication.service.MedicationReminderService;
import com.lww.service.RagService;
import com.lww.user.entity.User;
import com.lww.user.repository.UserRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 医疗专业工具集
 * 集成 RAG 服务和 MCP 工具服务
 */
public class MedicalTools {

    private static final Logger log = LoggerFactory.getLogger(MedicalTools.class);

    private final RagService ragService;
    private final McpToolService mcpToolService;
    private final MedicationReminderService reminderService;
    private final UserRepository userRepository;

    // 全局会话-用户映射存储（用 sessionId 获取用户 ID）
    private static final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    public MedicalTools(RagService ragService, McpToolService mcpToolService,
                        MedicationReminderService reminderService, UserRepository userRepository) {
        this.ragService = ragService;
        this.mcpToolService = mcpToolService;
        this.reminderService = reminderService;
        this.userRepository = userRepository;
    }

    /**
     * 绑定会话与用户ID（由 Controller 调用）
     */
    public static void bindSessionUser(String sessionId, Long userId) {
        sessionUserMap.put(sessionId, userId);
        log.info("Session {} bound to user {}", sessionId, userId);
    }

    /**
     * 解绑会话与用户ID
     */
    public static void unbindSessionUser(String sessionId) {
        sessionUserMap.remove(sessionId);
    }

    /**
     * 获取会话对应的用户ID
     */
    public static Long getSessionUserId(String sessionId) {
        return sessionUserMap.get(sessionId);
    }

    /**
     * 获取当前登录用户（通过 sessionId 获取）
     * 注意：工具调用发生在智谱AI的回调线程中，无法访问 SecurityContext
     */
    private User getCurrentUserBySession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("getCurrentUserBySession - sessionId is null or empty");
            return null;
        }

        Long userId = sessionUserMap.get(sessionId);
        if (userId == null) {
            log.warn("getCurrentUserBySession - no user bound for sessionId: {}", sessionId);
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        log.info("getCurrentUserBySession - sessionId: {}, userId: {}, user: {}",
                sessionId, userId, user != null ? user.getUsername() : "null");
        return user;
    }

    /**
     * 获取当前登录用户（兼容旧代码）
     * @deprecated 使用 getCurrentUserBySession(String sessionId) 替代
     */
    @Deprecated
    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            log.info("getCurrentUser - authentication: {}, thread: {}",
                    authentication != null ? authentication.getName() : "null",
                    Thread.currentThread().getName());

            if (authentication == null) {
                log.warn("getCurrentUser - authentication is null");
                return null;
            }
            if (!authentication.isAuthenticated()) {
                log.warn("getCurrentUser - not authenticated");
                return null;
            }

            String username = authentication.getName();

            if ("anonymousUser".equals(username)) {
                log.warn("getCurrentUser - anonymous user, please login first");
                return null;
            }

            User user = userRepository.findByUsername(username).orElse(null);
            log.info("getCurrentUser - user found: {}", user != null ? user.getUsername() : "null");
            return user;
        } catch (Exception e) {
            log.error("getCurrentUser error: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 基础医疗工具 ====================

    /**
     * 症状评估：输入症状描述，返回可能的疾病和紧急程度评分(1-10)，并从知识库检索相关信息
     */
    @Tool("症状评估：输入症状描述，返回可能的疾病和紧急程度评分(1-10)，并从知识库检索相关信息")
    public String assessSymptoms(@P("症状描述") String symptoms) {
        int urgency = calculateUrgency(symptoms);

        StringBuilder knowledgeContext = new StringBuilder();
        try {
            var results = ragService.search(symptoms, 3, 0.3);
            if (!results.isEmpty()) {
                knowledgeContext.append("\n【知识库参考】\n");
                for (var r : results) {
                    knowledgeContext.append("- ").append(r.getContent()).append("\n");
                }
            }
        } catch (Exception e) {
            knowledgeContext.append("\n（知识库检索失败：").append(e.getMessage()).append("）");
        }

        return String.format("紧急程度：%d/10\n建议咨询医生了解可能的疾病情况%s", urgency, knowledgeContext);
    }


    /**
     * 科室推荐：根据症状描述推荐合适的就诊科室
     */
    @Tool("科室推荐：根据症状描述推荐合适的就诊科室")
    public String recommendDepartment(@P("症状描述") String symptoms) {
        String lowerSymptoms = symptoms.toLowerCase();

        // 心内科 - 心脏相关症状
        if (containsAny(lowerSymptoms, "胸痛", "chest pain", "心脏", "heart", "心悸", "心慌")) {
            return "推荐科室：心内科";
        }
        // 神经内科 - 神经系统症状
        if (containsAny(lowerSymptoms, "头晕", "dizziness", "麻木", "numbness", "头痛", "headache", "手脚麻")) {
            return "推荐科室：神经内科";
        }
        // 外科 - 外伤相关
        if (containsAny(lowerSymptoms, "外伤", "injury", "骨折", "fracture", "摔伤", "割伤")) {
            return "推荐科室：外科";
        }
        // 消化内科 - 消化系统症状
        if (containsAny(lowerSymptoms, "胃痛", "stomach", "消化", "digestion", "腹胀", "腹泻", "便秘", "恶心")) {
            return "推荐科室：消化内科";
        }
        // 呼吸内科 - 呼吸系统症状
        if (containsAny(lowerSymptoms, "咳嗽", "cough", "发烧", "fever", "发热", "呼吸困难", "胸闷", "气短")) {
            return "推荐科室：呼吸内科";
        }
        // 骨科 - 骨骼相关
        if (containsAny(lowerSymptoms, "腰痛", "关节痛", "背痛", "颈椎", "腰椎", "膝盖")) {
            return "推荐科室：骨科";
        }
        // 皮肤科 - 皮肤问题
        if (containsAny(lowerSymptoms, "皮疹", "瘙痒", "红肿", "过敏", "皮肤")) {
            return "推荐科室：皮肤科";
        }
        // 眼科 - 眼部问题
        if (containsAny(lowerSymptoms, "眼睛", "视力", "眼痛", "流泪", "眼睛红")) {
            return "推荐科室：眼科";
        }
        // 耳鼻喉科 - 耳鼻喉问题
        if (containsAny(lowerSymptoms, "耳朵", "耳鸣", "鼻子", "喉咙", "嗓子", "鼻塞")) {
            return "推荐科室：耳鼻喉科";
        }

        return "推荐科室：内科（建议先进行基础检查）";
    }

    /**
     * 医学知识检索：从知识库中检索医学健康知识
     */
    @Tool("医学知识检索：从知识库中检索医学健康知识")
    public String searchMedicalKnowledge(@P("查询内容") String query, @P("返回结果数量，默认5条") Integer topK) {
        try {
            var results = ragService.search(query, topK == null ? 5 : topK, 0.3);
            if (results.isEmpty()) {
                return "未找到相关医学知识，请咨询专业医生。";
            }
            StringBuilder sb = new StringBuilder("【医学知识库检索结果】\n");
            int idx = 1;
            for (var r : results) {
                sb.append(idx++).append(". ").append(r.getContent()).append("\n");
            }
            sb.append("\n注意：以上内容仅供参考，用药和治疗请遵医嘱。");
            return sb.toString();
        } catch (Exception e) {
            return "知识库检索出错：" + e.getMessage();
        }
    }

    // ==================== MCP 增强工具 ====================

    /**
     * 深度症状分析：使用 Sequential Thinking 进行结构化推理
     */
    @Tool("深度症状分析：使用AI推理引擎进行多步骤症状分析，提供更详细的健康建议")
    public String deepAnalyzeSymptoms(@P("症状描述") String symptoms) {
        try {
            // 先获取知识库信息作为上下文
            StringBuilder context = new StringBuilder();
            try {
                var results = ragService.search(symptoms, 2, 0.3);
                for (var r : results) {
                    context.append(r.getContent()).append("\n");
                }
            } catch (Exception e) {
                log.debug("RAG context fetch failed for deep analysis");
            }

            // 调用 MCP Sequential Thinking 进行推理
            return mcpToolService.analyzeSymptomsWithReasoning(symptoms);
        } catch (Exception e) {
            log.warn("Deep analysis failed, falling back to basic assessment", e);
            return assessSymptoms(symptoms);
        }
    }

    /**
     * 存储健康记忆：将用户的重要健康信息存储到持久化记忆系统
     */
    @Tool("存储健康记忆：将用户的重要健康信息（如病史、过敏史、用药记录）存储到记忆系统")
    public String storeHealthMemory(@P("会话ID") String sessionId,
                                    @P("健康信息内容") String healthInfo,
                                    @P("信息类型(病史/过敏/用药/症状)") String infoType) {
        String memoryContent = String.format("[%s] %s", infoType, healthInfo);
        return mcpToolService.storeMemory(sessionId, memoryContent, "system");
    }

    /**
     * 检索健康记忆：从记忆系统中检索用户的历史健康信息
     */
    @Tool("检索健康记忆：检索用户的历史健康信息，包括病史、过敏史、用药记录等")
    public String retrieveHealthMemory(@P("会话ID") String sessionId,
                                       @P("查询关键词") String keyword) {
        return mcpToolService.retrieveMemory(sessionId, keyword);
    }

    /**
     * 用药提醒：创建用药提醒并计算下次服药时间
     */
    @Tool("用药提醒：创建用药提醒，包括药品名称、服用时间和用药说明")
    public String createMedicationReminder(@P("药品名称") String medicationName,
                                           @P("服用时间") String time,
                                           @P("用药说明") String instructions) {
        return mcpToolService.createMedicationReminder(medicationName, time, instructions);
    }

    /**
     * 预约时间计算：计算合适的预约就诊时间
     */
    @Tool("预约时间计算：根据当前时间和紧急程度，建议合适的预约就诊时间")
    public String suggestAppointmentTime(@P("紧急程度(1-10)") int urgencyLevel,
                                         @P("科室") String department) {
        String currentTime = mcpToolService.getCurrentTime("Asia/Shanghai");

        String urgencyAdvice;
        if (urgencyLevel >= 8) {
            urgencyAdvice = "【紧急】建议立即就医或拨打120急救电话";
        } else if (urgencyLevel >= 6) {
            urgencyAdvice = "【较急】建议今天内就诊，可挂急诊";
        } else if (urgencyLevel >= 4) {
            urgencyAdvice = "【一般】建议3天内预约" + department + "门诊";
        } else {
            urgencyAdvice = "【常规】建议一周内预约" + department + "门诊";
        }

        return String.format("当前时间：%s\n%s", currentTime, urgencyAdvice);
    }

    // ==================== 用药提醒工具 ====================

    /**
     * 创建用药提醒计划：用户告诉药品名称和服药时间，自动创建提醒记录
     * 注意：sessionId 参数由系统自动填充，用于识别当前登录用户
     */
    @Tool("创建用药提醒计划：用户告诉药品名称和服药时间，自动在系统中创建提醒记录。例如用户说'我要每天8点吃阿司匹林'。参数sessionId由系统自动提供，不需要用户输入。")
    public String createMedicationReminderPlan(
            @P("会话ID，由系统自动填充") String sessionId,
            @P("药品名称") String medicationName,
            @P("服药时间，如08:00，多个时间用逗号分隔") String times,
            @P("剂量，如1片，可选") String dosage,
            @P("频率：每天/每周，默认每天") String frequency,
            @P("备注说明，可选") String notes) {

        log.info("createMedicationReminderPlan called - sessionId: {}, medication: {}, times: {}",
                sessionId, medicationName, times);

        User currentUser = getCurrentUserBySession(sessionId);
        if (currentUser == null) {
            return "请先登录后再创建用药提醒。";
        }

        try {
            // 构建 ReminderRequest
            ReminderRequest request = new ReminderRequest();
            request.setMedicationName(medicationName);
            request.setDosage(dosage != null ? dosage : "按医嘱");

            // 解析时间
            List<String> remindTimes = Arrays.stream(times.split("[,，、]"))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());

            if (remindTimes.isEmpty()) {
                return "请提供服药时间，例如：08:00";
            }

            request.setRemindTimes(remindTimes);

            // 设置频率
            if (frequency != null && frequency.contains("周")) {
                request.setFrequency("WEEKLY");
                request.setDaysOfWeek("1,2,3,4,5,6,7"); // 默认每周都提醒
            } else {
                request.setFrequency("DAILY");
                request.setDaysOfWeek("1,2,3,4,5,6,7");
            }

            request.setStartDate(LocalDate.now());
            request.setNotes(notes);

            // 调用服务创建提醒
            ReminderResponse response = reminderService.createReminder(currentUser.getId(), request);

            return String.format("✅ 用药提醒创建成功！\n" +
                            "药品：%s\n" +
                            "剂量：%s\n" +
                            "服药时间：%s\n" +
                            "频率：%s\n" +
                            "系统将在指定时间提醒您服药。",
                    response.getMedicationName(),
                    response.getDosage(),
                    String.join("、", response.getRemindTimes()),
                    "DAILY".equals(response.getFrequency()) ? "每天" : "每周");

        } catch (Exception e) {
            log.error("创建用药提醒失败", e);
            return "创建用药提醒失败：" + e.getMessage();
        }
    }

    /**
     * 查询我的用药提醒：获取当前用户的所有用药提醒计划
     * 注意：sessionId 参数由系统自动填充，用于识别当前登录用户
     */
    @Tool("查询我的用药提醒：获取当前登录用户的所有用药提醒计划列表。参数sessionId由系统自动提供，不需要用户输入。")
    public String getMyMedicationReminders(@P("会话ID，由系统自动填充") String sessionId) {
        log.info("getMyMedicationReminders called - sessionId: {}", sessionId);

        User currentUser = getCurrentUserBySession(sessionId);
        if (currentUser == null) {
            return "请先登录后查看用药提醒。";
        }

        try {
            List<ReminderResponse> reminders = reminderService.getUserReminders(currentUser.getId());

            if (reminders.isEmpty()) {
                return "您还没有设置任何用药提醒。可以告诉我'我要每天X点吃XX药'来创建提醒。";
            }

            StringBuilder sb = new StringBuilder("📋 您的用药提醒计划：\n\n");
            int idx = 1;
            for (ReminderResponse r : reminders) {
                sb.append(idx++).append(". ").append(r.getMedicationName())
                        .append(" - ").append(r.getDosage())
                        .append(" - ").append(String.join("、", r.getRemindTimes()))
                        .append(r.getEnabled() ? " [启用]" : " [已停用]")
                        .append("\n");
            }
            sb.append("\n提示：可以说'我要停用XX药的提醒'来管理提醒计划。");

            return sb.toString();
        } catch (Exception e) {
            log.error("查询用药提醒失败", e);
            return "查询用药提醒失败：" + e.getMessage();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算症状紧急程度评分 (1-10)
     */
    private int calculateUrgency(String symptoms) {
        String lowerSymptoms = symptoms.toLowerCase();

        // 最高紧急度 (10) - 危及生命
        if (containsAny(lowerSymptoms, "大出血", "bleeding", "昏迷", "unconscious", "意识丧失", "心脏骤停")) {
            return 10;
        }
        // 高紧急度 (9) - 严重情况
        if (containsAny(lowerSymptoms, "胸痛", "chest pain", "呼吸困难", "breathing difficulty", "气短", "窒息")) {
            return 9;
        }
        // 较高紧急度 (7-8)
        if (containsAny(lowerSymptoms, "剧烈头痛", "突然失语", "偏瘫", "剧烈腹痛")) {
            return 8;
        }
        // 中等紧急度 (5-6)
        if (containsAny(lowerSymptoms, "发烧", "fever", "咳嗽", "cough", "呕吐", "腹泻", "脱水")) {
            return 6;
        }
        // 较低紧急度 (3-4)
        if (containsAny(lowerSymptoms, "头晕", "dizziness", "疲劳", "fatigue", "乏力", "失眠")) {
            return 4;
        }
        // 一般紧急度
        return 3;
    }

    /**
     * 检查字符串是否包含任意一个关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
