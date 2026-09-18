package com.ccnlthd.course_management.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", "UP");
        response.put(
                "application",
                "Course Management API"
        );
        response.put(
                "timestamp",
                LocalDateTime.now()
        );

        return ResponseEntity.ok(response);
    }
}