package com.example.event_library.events;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true) // Cực quan trọng: Bỏ qua các trường lạ/thiếu
@JsonInclude(JsonInclude.Include.NON_NULL)  // Không serialize field null vào JSON
public class InternalCommunicationForRecommendation {
    private String recommendationId;
    private String productId;
    private String recommendationText;
}
