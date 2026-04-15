package com.lww;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成示例医学知识 PDF - 简化版
 */
public class SamplePdfGenerator {

    public static void main(String[] args) throws Exception {
        // 使用绝对路径
        Path outputDir = Paths.get("D:/LWW/AsolfOpen/SpringAiAlibaba-v1/SAA-01HelloWorld/data");
        Files.createDirectories(outputDir);
        String outputPath = outputDir.resolve("sample.pdf").toString();

        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType1Font.HELVETICA;
            PDFont boldFont = PDType1Font.HELVETICA_BOLD;

            // 封面
            PDPage coverPage = new PDPage();
            document.addPage(coverPage);
            try (PDPageContentStream content = new PDPageContentStream(document, coverPage)) {
                content.beginText();
                content.setFont(boldFont, 28);
                content.newLineAtOffset(100, 680);
                content.showText("Medical Knowledge Handbook");
                content.endText();

                content.beginText();
                content.setFont(font, 16);
                content.newLineAtOffset(130, 620);
                content.showText("Common Disease Prevention Guide");
                content.endText();

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(180, 550);
                content.showText("By GAGA AI Medical Assistant");
                content.endText();
            }

            // 章节页 1
            addChapter(document, boldFont, font,
                "Chapter 1: Hypertension Prevention",
                new String[]{
                    "Overview: Hypertension is one of the most common chronic diseases,",
                    "often called the 'silent killer'.",
                    "",
                    "Main Symptoms:",
                    "- Headache and dizziness",
                    "- Blurred vision",
                    "- Nosebleeds",
                    "- Chest tightness, palpitations",
                    "",
                    "Prevention:",
                    "1. Limit sodium intake (max 6g/day)",
                    "2. Maintain healthy weight (BMI 18.5-24)",
                    "3. Regular exercise (150 min/week)",
                    "4. Quit smoking, limit alcohol",
                    "5. Manage stress, maintain good mental health",
                    "",
                    "Treatment:",
                    "- Lifestyle intervention is the foundation",
                    "- Medication requires doctor's guidance",
                    "- Regular blood pressure monitoring",
                    "- Long-term medication adherence"
                }
            );

            // 章节页 2
            addChapter(document, boldFont, font,
                "Chapter 2: Diabetes Management",
                new String[]{
                    "Overview: Diabetes is a metabolic disease characterized by",
                    "high blood sugar levels.",
                    "",
                    "Types:",
                    "- Type 1: Absolute insulin deficiency",
                    "- Type 2: Relative insulin deficiency or resistance",
                    "- Gestational: High blood sugar during pregnancy",
                    "",
                    "Classic Symptoms (3 Polys + 1 Less):",
                    "1. Polyuria: increased urination",
                    "2. Polydipsia: increased thirst",
                    "3. Polyphagia: increased hunger",
                    "4. Weight loss: unexplained",
                    "",
                    "Blood Sugar Targets:",
                    "- Fasting: 4.4-7.0 mmol/L",
                    "- Post-meal (2h): <10.0 mmol/L",
                    "- HbA1c: <7.0%",
                    "",
                    "Diet: Choose low-GI foods, moderate protein, limit refined sugars"
                }
            );

            // 章节页 3
            addChapter(document, boldFont, font,
                "Chapter 3: Flu Prevention",
                new String[]{
                    "Overview: Influenza is an acute respiratory infection",
                    "caused by influenza viruses.",
                    "",
                    "vs Common Cold:",
                    "- Flu: sudden onset, high fever (39-40C)",
                    "- Cold: mild symptoms, low or no fever",
                    "- Flu: systemic symptoms (headache, muscle pain, fatigue)",
                    "- Cold: primarily local nasopharyngeal symptoms",
                    "",
                    "Transmission:",
                    "1. Droplets: coughing, sneezing",
                    "2. Contact: touching contaminated surfaces",
                    "3. Aerosol: in enclosed spaces",
                    "",
                    "Prevention:",
                    "- Annual flu vaccination (autumn)",
                    "- Wash hands frequently",
                    "- Keep rooms ventilated",
                    "- Avoid crowded places",
                    "- Boost immunity: balanced diet, regular sleep",
                    "",
                    "Seek medical help promptly, especially for high-risk groups"
                }
            );

            // 章节页 4
            addChapter(document, boldFont, font,
                "Chapter 4: Mental Health",
                new String[]{
                    "Overview: Mental health is a vital component of overall health.",
                    "",
                    "Warning Signs:",
                    "- Persistent low mood for over 2 weeks",
                    "- Loss of interest in usual activities",
                    "- Sleep disturbances: insomnia or hypersomnia",
                    "- Significant appetite changes",
                    "- Difficulty concentrating",
                    "- Feelings of worthlessness or excessive guilt",
                    "",
                    "Stress Management:",
                    "1. Time management: balance work and rest",
                    "2. Exercise: 3-5 times/week aerobic activity",
                    "3. Breathing exercises: deep breathing for relaxation",
                    "4. Social support: talk with friends and family",
                    "5. Hobbies: develop beneficial interests",
                    "",
                    "Seek Professional Help:",
                    "When self-regulation fails, consult a psychologist or psychiatrist"
                }
            );

            document.save(outputPath);
            System.out.println("PDF generated successfully: " + outputPath);
        }
    }

    private static void addChapter(PDDocument document, PDFont boldFont, PDFont font,
                                   String title, String[] lines) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(boldFont, 16);
            content.newLineAtOffset(50, 750);
            content.showText(title);
            content.endText();

            content.beginText();
            content.setFont(font, 10);
            content.setLeading(14);
            float y = 710;
            content.newLineAtOffset(50, y);

            for (String line : lines) {
                if (line.isEmpty()) {
                    y -= 6;
                    content.newLineAtOffset(0, -6);
                } else {
                    content.showText(line);
                }
                content.newLine();
                y -= 14;
                if (y < 50) break;
            }
            content.endText();
        }
    }
}
