package com.example.rec_service.controller;

import com.example.rec_service.entity.Product;
import com.example.rec_service.entity.ProductProjection;
import com.example.rec_service.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/repository")
@RequiredArgsConstructor
public class StockInController {
    private final RecommendService recommendService;

    @PostMapping
    public ResponseEntity<String> stockIn(@Valid @RequestBody ProductProjection productProjection){
        String result = recommendService.saveProducts(productProjection);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
