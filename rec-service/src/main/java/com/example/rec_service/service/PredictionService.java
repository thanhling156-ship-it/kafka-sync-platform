package com.example.rec_service.service;

import com.example.event_library.events.ShipCreatedEvent;
import com.example.rec_service.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class PredictionService {

    private final RestTemplate restTemplate = new RestTemplate();

    public void notificationAnomaly(ShipCreatedEvent event){
        String orderId =  event.getOrderId();
        int quantity = event.getQuantity();
        double unitPrice = event.getTotalAmount()/quantity;
        String result = callPythonPredict(unitPrice, quantity);
        log.info("Order Id: " + orderId + "--------"+result);
    }

    public String callPythonPredict(double price, double quantity) {
        // URL của Flask API (không kèm tham số trên URL nữa)
        String url = "http://host.docker.internal:5000/api/predict";

        try {
            // 1. Tạo JSON Body dưới dạng Map để gửi đi
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("price", price);
            requestBody.put("quantity", quantity);

            // 2. Sử dụng postForObject để gửi POST request với JSON body
            ApiResponse response = restTemplate.postForObject(url, requestBody, ApiResponse.class);

            if (response != null && "success".equals(response.getStatus())) {
                String label = response.getResult();
                double xacSuat = response.getProbability();
                return String.format(" Kết quả: %s | Xác suất: %.4f ", label, xacSuat);
            } else {
                return "ERROR: API trả về trạng thái thất bại hoặc phản hồi rỗng";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "ERROR";
    }
}
