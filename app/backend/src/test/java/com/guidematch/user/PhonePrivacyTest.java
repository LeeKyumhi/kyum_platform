package com.guidematch.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 구매자 연락처는 결제(PG)와 본인에게만 가고, 상대방(가이드/여행자)에게는 절대 노출되지 않아야 한다.
 *
 * 이 규칙은 "우리가 기억하고 있는 것"이 아니라 테스트로 고정한다. 응답 DTO에 phone류 필드를
 * 새로 추가하면 이 테스트가 깨지고, 정말 본인 전용이라면 아래 ALLOWED에 근거와 함께 추가해야 한다.
 */
class PhonePrivacyTest {

    /** 연락처가 있어도 되는 곳 — 각각 왜 괜찮은지 근거가 있어야 한다. */
    private static final Set<String> ALLOWED = Set.of(
            // 본인 조회 전용 DTO (signup 응답 · GET /me · 본인 수정 응답에만 쓰인다)
            "auth/dto/UserResponse.java",
            // 본인이 자기 결제창을 여는 데 쓰는 값. 서버가 본인 것만 채운다.
            "payment/dto/PreparePaymentResponse.java",
            // 위 응답을 채우는 곳 — travelerId(요청한 본인)로만 조회한다. 상대방 조회 경로 없음.
            "payment/PaymentService.java",
            // 사용자 연락처가 아니라 '장소'의 대표번호(카카오 로컬 데이터)
            "geo/PlaceController.java",
            "geo/KakaoLocalClient.java"
    );

    @Test
    void 응답DTO에_사용자_연락처가_새지_않는다() throws IOException {
        Path root = Paths.get("src/main/java/com/guidematch");
        assertTrue(Files.isDirectory(root), "소스 루트를 찾지 못했습니다: " + root.toAbsolutePath());

        List<String> leaks = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                // 클라이언트로 나가는 응답 형태만 본다: dto 패키지 + *Response/*Row 클래스
                boolean isOutbound = rel.contains("/dto/")
                        || rel.endsWith("Response.java")
                        || rel.endsWith("Row.java")
                        || rel.endsWith("Service.java"); // 인라인 record(예: AdminUserService.UserRow)
                if (!isOutbound || ALLOWED.contains(rel)) continue;

                String src = Files.readString(p);
                if (src.toLowerCase().contains("phone")) {
                    leaks.add(rel);
                }
            }
        }

        assertTrue(leaks.isEmpty(),
                "상대방에게 나갈 수 있는 응답에 연락처(phone)가 들어갔습니다. "
                        + "본인 전용이 확실하면 PhonePrivacyTest.ALLOWED에 근거와 함께 추가하세요: " + leaks);
    }
}
