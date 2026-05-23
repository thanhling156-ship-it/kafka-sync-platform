package com.example.rec_service.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails; // Class quan trọng nhất lúc này
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;



import java.util.List;
import java.util.Map;

@Configuration
public class AIConfig {

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;


    /*Quan trọng để tra cứu model chuẩn
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails) {
        try {
            System.out.println("--- ĐANG QUÉT DANH SÁCH MODEL ---");
            var client = connectionDetails.getGenAiClient(); //
            var config = com.google.genai.types.ListModelsConfig.builder().build();
            var pager = client.models.list(config); //

            pager.forEach(m -> {
                // name() và supportedActions() trả về Optional nên cần .orElse()
                String modelName = m.name().orElse("N/A");
                var actions = m.supportedActions().orElse(java.util.Collections.emptyList());

                // In ra để AI Architect kiểm tra "địa chỉ" model
                System.out.println("Model: " + modelName + " | Actions: " + actions);
            });
            System.out.println("---------------------------------");
        } catch (Exception e) {
            System.err.println("Lỗi khi quét model: " + e.getMessage());
        }

        // Sau khi xem log, hãy điền tên CHÍNH XÁC (có hoặc không có tiền tố models/)
        var options = GoogleGenAiTextEmbeddingOptions.builder()
                .model("text-embedding-004")
                .build();

        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

     */


    // 3.Khai báo máy dịch - EmbeddingModel
    @Bean
    public EmbeddingModel embeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails) {
        var options = GoogleGenAiTextEmbeddingOptions.builder()
                .model("models/gemini-embedding-2")
                .dimensions(768) // Khớp với Database của bạn
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT) // Tối ưu cho việc lưu trữ
                .build();
        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

    // 2. Cấu hình cho Chat (Dùng safetySettings để tránh lỗi "No value present")
    @Bean
    public GoogleGenAiChatOptions chatOptions() {
        return GoogleGenAiChatOptions.builder()
                .model("gemini-2.5-flash-lite")
                .safetySettings(List.of(
                        new GoogleGenAiSafetySetting(
                                // Tham số 1: Category
                                GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,

                                // Tham số 2: Threshold - Dùng BLOCK_ONLY_HIGH để tránh lỗi "No value present"
                                GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH,

                                // Tham số 3: Method - Phải có đủ 3 tham số theo constructor
                                GoogleGenAiSafetySetting.HarmBlockMethod.HARM_BLOCK_METHOD_UNSPECIFIED
                        )
                ))
                .build();
    }

    // 1. Tạo Bean kết nối - Đây là thứ mà log lỗi đang đòi hỏi
    @Bean
    public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails() {
        return GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(apiKey)
                .build();
    }
}