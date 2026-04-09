package com.lww.mcp;

import com.lww.medical.tools.MedicalTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册器
 * 将 MedicalTools 注册到 MCP Server
 */
@Component
public class MedicalMcpToolRegistrar {

    private final McpSyncServer mcpSyncServer;
    private final MedicalTools medicalTools;

    public MedicalMcpToolRegistrar(McpSyncServer mcpSyncServer, MedicalTools medicalTools) {
        this.mcpSyncServer = mcpSyncServer;
        this.medicalTools = medicalTools;
    }

    @PostConstruct
    public void registerTools() {
        // 注册症状评估工具
        mcpSyncServer.addTool(createToolSpecification(
                "assess_symptoms",
                "症状评估：输入症状描述，返回可能的疾病和紧急程度评分(1-10)，并从知识库检索相关信息",
                List.of("symptoms"),
                (exchange, args) -> {
                    String symptoms = (String) args.get("symptoms");
                    String result = medicalTools.assessSymptoms(symptoms);
                    return createTextResult(result);
                }
        ));

        // 注册科室推荐工具
        mcpSyncServer.addTool(createToolSpecification(
                "recommend_department",
                "科室推荐：根据症状描述推荐合适的就诊科室",
                List.of("symptoms"),
                (exchange, args) -> {
                    String symptoms = (String) args.get("symptoms");
                    String result = medicalTools.recommendDepartment(symptoms);
                    return createTextResult(result);
                }
        ));

        // 注册医学知识检索工具（带可选参数）
        McpSchema.Tool searchTool = new McpSchema.Tool(
                "search_medical_knowledge",
                "医学知识检索：从知识库中检索医学健康知识",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"top_k\":{\"type\":\"integer\"}},\"required\":[\"query\"]}"
        );
        mcpSyncServer.addTool(new McpServerFeatures.SyncToolSpecification(
                searchTool,
                (exchange, args) -> {
                    String query = (String) args.get("query");
                    Integer topK = args.get("top_k") != null ? ((Number) args.get("top_k")).intValue() : 5;
                    String result = medicalTools.searchMedicalKnowledge(query, topK);
                    return createTextResult(result);
                }
        ));

        // 注册深度症状分析工具
        mcpSyncServer.addTool(createToolSpecification(
                "deep_analyze_symptoms",
                "深度症状分析：使用AI推理引擎进行多步骤症状分析，提供更详细的健康建议",
                List.of("symptoms"),
                (exchange, args) -> {
                    String symptoms = (String) args.get("symptoms");
                    String result = medicalTools.deepAnalyzeSymptoms(symptoms);
                    return createTextResult(result);
                }
        ));

        // 注册存储健康记忆工具
        mcpSyncServer.addTool(createToolSpecificationWithMultipleParams(
                "store_health_memory",
                "存储健康记忆：将用户的重要健康信息（如病史、过敏史、用药记录）存储到记忆系统",
                List.of("session_id", "health_info", "info_type"),
                (exchange, args) -> {
                    String sessionId = (String) args.get("session_id");
                    String healthInfo = (String) args.get("health_info");
                    String infoType = (String) args.get("info_type");
                    String result = medicalTools.storeHealthMemory(sessionId, healthInfo, infoType);
                    return createTextResult(result);
                }
        ));

        // 注册检索健康记忆工具
        mcpSyncServer.addTool(createToolSpecificationWithMultipleParams(
                "retrieve_health_memory",
                "检索健康记忆：检索用户的历史健康信息，包括病史、过敏史、用药记录等",
                List.of("session_id", "keyword"),
                (exchange, args) -> {
                    String sessionId = (String) args.get("session_id");
                    String keyword = (String) args.get("keyword");
                    String result = medicalTools.retrieveHealthMemory(sessionId, keyword);
                    return createTextResult(result);
                }
        ));

        // 注册用药提醒工具
        mcpSyncServer.addTool(createToolSpecificationWithMultipleParams(
                "create_medication_reminder",
                "用药提醒：创建用药提醒，包括药品名称、服用时间和用药说明",
                List.of("medication_name", "time", "instructions"),
                (exchange, args) -> {
                    String medicationName = (String) args.get("medication_name");
                    String time = (String) args.get("time");
                    String instructions = (String) args.get("instructions");
                    String result = medicalTools.createMedicationReminder(medicationName, time, instructions);
                    return createTextResult(result);
                }
        ));

        // 注册预约时间计算工具
        mcpSyncServer.addTool(createToolSpecificationWithMultipleParams(
                "suggest_appointment_time",
                "预约时间计算：根据当前时间和紧急程度，建议合适的预约就诊时间",
                List.of("urgency_level", "department"),
                (exchange, args) -> {
                    int urgencyLevel = ((Number) args.get("urgency_level")).intValue();
                    String department = (String) args.get("department");
                    String result = medicalTools.suggestAppointmentTime(urgencyLevel, department);
                    return createTextResult(result);
                }
        ));
    }

    /**
     * 创建工具规格（简化版，用于只有必需参数的工具）
     */
    private McpServerFeatures.SyncToolSpecification createToolSpecification(
            String name,
            String description,
            List<String> requiredParams,
            java.util.function.BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler) {

        StringBuilder inputSchema = new StringBuilder();
        inputSchema.append("{\"type\":\"object\",\"properties\":{");
        for (int i = 0; i < requiredParams.size(); i++) {
            if (i > 0) inputSchema.append(",");
            inputSchema.append("\"").append(requiredParams.get(i)).append("\":{\"type\":\"string\"}");
        }
        inputSchema.append("},\"required\":[");
        for (int i = 0; i < requiredParams.size(); i++) {
            if (i > 0) inputSchema.append(",");
            inputSchema.append("\"").append(requiredParams.get(i)).append("\"");
        }
        inputSchema.append("]}");

        McpSchema.Tool tool = new McpSchema.Tool(name, description, inputSchema.toString());
        return new McpServerFeatures.SyncToolSpecification(tool, handler);
    }

    /**
     * 创建工具规格（支持多参数类型）
     */
    private McpServerFeatures.SyncToolSpecification createToolSpecificationWithMultipleParams(
            String name,
            String description,
            List<String> requiredParams,
            java.util.function.BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler) {

        StringBuilder inputSchema = new StringBuilder();
        inputSchema.append("{\"type\":\"object\",\"properties\":{");
        for (int i = 0; i < requiredParams.size(); i++) {
            if (i > 0) inputSchema.append(",");
            String param = requiredParams.get(i);
            // urgency_level 是整数类型
            if (param.equals("urgency_level")) {
                inputSchema.append("\"").append(param).append("\":{\"type\":\"integer\"}");
            } else {
                inputSchema.append("\"").append(param).append("\":{\"type\":\"string\"}");
            }
        }
        inputSchema.append("},\"required\":[");
        for (int i = 0; i < requiredParams.size(); i++) {
            if (i > 0) inputSchema.append(",");
            inputSchema.append("\"").append(requiredParams.get(i)).append("\"");
        }
        inputSchema.append("]}");

        McpSchema.Tool tool = new McpSchema.Tool(name, description, inputSchema.toString());
        return new McpServerFeatures.SyncToolSpecification(tool, handler);
    }

    /**
     * 创建文本类型的结果
     */
    private McpSchema.CallToolResult createTextResult(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }
}
