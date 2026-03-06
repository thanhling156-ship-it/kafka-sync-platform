package com.example.product_service.service;

import com.example.product_service.entity.Product;
import com.example.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Product createProduct(Product product) {
        // Validation nhẹ nhàng
        if (product.getPrice() < 0) throw new RuntimeException("Giá không được âm!");
        if (product.getQuantity() < 0) throw new RuntimeException("Số lượng không hợp lệ!");

        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}
