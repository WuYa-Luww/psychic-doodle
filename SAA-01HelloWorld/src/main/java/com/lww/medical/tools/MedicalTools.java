package com.lww.medical.tools;

import com.lww.service.RagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.*;

/**
 * Medical professional tools
 * Integrated with RAG service for medical knowledge retrieval from Milvus
 */
public class MedicalTools {

    private final RagService ragService;

    public MedicalTools(RagService ragService) {
        this.ragService = ragService;
    }

    @Tool("Symptom assessment: input symptom description, return possible diseases and urgency score (1-10), retrieve related info from knowledge base")
    public String assessSymptoms(@P("Symptom description") String symptoms) {
        int urgency = calculateUrgency(symptoms);

        StringBuilder knowledgeContext = new StringBuilder();
        try {
            var results = ragService.search(symptoms, 3, 0.3);
            if (!results.isEmpty()) {
                knowledgeContext.append("\n[Knowledge Base Reference]\n");
                for (var r : results) {
                    knowledgeContext.append("- ").append(r.getContent()).append("\n");
                }
            }
        } catch (Exception e) {
            knowledgeContext.append("\n(Knowledge base search failed: ").append(e.getMessage()).append(")");
        }

        return String.format("Urgency: %d/10\nPlease consult doctor for possible conditions%s", urgency, knowledgeContext);
    }

    @Tool("Department recommendation: recommend medical department based on symptoms")
    public String recommendDepartment(@P("Symptoms") String symptoms) {
        if (symptoms.contains("chest pain") || symptoms.contains("heart")) return "Recommended: Cardiology";
        if (symptoms.contains("dizziness") || symptoms.contains("numbness")) return "Recommended: Neurology";
        if (symptoms.contains("injury") || symptoms.contains("fracture")) return "Recommended: Surgery";
        if (symptoms.contains("stomach") || symptoms.contains("digestion")) return "Recommended: Gastroenterology";
        if (symptoms.contains("cough") || symptoms.contains("fever")) return "Recommended: Respiratory Medicine";
        return "Recommended: Internal Medicine";
    }

    @Tool("Medical knowledge search: retrieve medical health knowledge from knowledge base")
    public String searchMedicalKnowledge(@P("Query content") String query, @P("Number of results") Integer topK) {
        try {
            var results = ragService.search(query, topK == null ? 5 : topK, 0.3);
            if (results.isEmpty()) {
                return "No relevant medical knowledge found, please consult a doctor.";
            }
            StringBuilder sb = new StringBuilder("[Medical Knowledge Base Results]\n");
            int idx = 1;
            for (var r : results) {
                sb.append(idx++).append(". ").append(r.getContent()).append("\n");
            }
            sb.append("\nNote: The above is for reference only, please follow doctor's advice for medication and treatment.");
            return sb.toString();
        } catch (Exception e) {
            return "Knowledge base search error: " + e.getMessage();
        }
    }

    private int calculateUrgency(String symptoms) {
        if (symptoms.contains("chest pain") || symptoms.contains("breathing difficulty")) return 9;
        if (symptoms.contains("bleeding") || symptoms.contains("unconscious")) return 10;
        if (symptoms.contains("fever") && symptoms.contains("cough")) return 6;
        if (symptoms.contains("dizziness") || symptoms.contains("fatigue")) return 4;
        return 3;
    }
}
