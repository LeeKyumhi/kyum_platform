package com.guidematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션의 시작점(entry point).
 * main 메서드가 실행되면 내장 웹 서버(Tomcat)가 켜진다.
 *
 * @SpringBootApplication = 스프링 부트 설정을 한 번에 켜주는 어노테이션.
 *   - 자동 설정(auto-configuration)
 *   - 이 패키지 하위의 컴포넌트(컨트롤러 등) 자동 스캔
 */
@SpringBootApplication
public class GuideMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuideMatchApplication.class, args);
    }
}
