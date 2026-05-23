package com.example.event_library.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestForRecommendation {
    private String recommendationId;
    private String userId;
    private String productId;
    private String recommendationText;
}
