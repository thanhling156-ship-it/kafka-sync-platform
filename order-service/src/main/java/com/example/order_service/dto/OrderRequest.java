package com.example.order_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderRequest {
    @NotBlank(message = "UserId không được để trống")
    private String userId;

    @NotBlank(message = "ProductId không được để trống")
    private String productId;

    @Min(value = 1, message = "Số lượng phải ít nhất là 1")
    private int quantity;

    @Positive(message = "Giá tiền phải là số dương")
    private double unitPrice; // Giá mỗi sản phẩm

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    @NotBlank(message = "SĐT không được để trống")
    private String number;
}