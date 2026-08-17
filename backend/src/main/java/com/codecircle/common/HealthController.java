package com.codecircle.common;
import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestController public class HealthController {@GetMapping("/health") Map<String,String> health(){return Map.of("status","ok","service","codecircle-backend");}}
