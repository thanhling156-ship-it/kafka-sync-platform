package com.example.rec_service.dto;

import lombok.Data;

import lombok.Data;
import java.util.Map;

@Data
public class ApiResponse {
    private String status;
    private String result;
    private double probability;
    private Map<String, Double> allProbs; // Nhận cấu trúc "all_probs" từ Python
}