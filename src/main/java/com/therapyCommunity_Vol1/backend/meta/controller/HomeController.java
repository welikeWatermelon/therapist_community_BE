package com.therapyCommunity_Vol1.backend.meta.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {
    @GetMapping("/api/v1/home")
    public ResponseEntity<ApiResponse<Map<String, String>>> home() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "home api working")));
    }
}
