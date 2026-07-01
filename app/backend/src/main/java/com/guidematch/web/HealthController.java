package com.guidematch.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버가 살아있는지 확인하는 가장 기본적인 API.
 *
 * @RestController = 이 클래스의 메서드 반환값을 그대로 HTTP 응답(JSON)으로 보낸다.
 * @RequestMapping("/api") = 이 클래스의 모든 주소 앞에 /api 를 붙인다.
 *
 * 결과: GET http://localhost:8080/api/health 호출 시
 *      {"status":"ok","service":"guide-match-backend"} 반환
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "guide-match-backend"
        );
    }
}
