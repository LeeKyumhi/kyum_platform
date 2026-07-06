package com.guidematch.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 데이터를 담는 DTO.
 * DTO(Data Transfer Object) = 계층 간에 데이터를 실어 나르는 그릇.
 * 엔티티(User)를 그대로 외부에 노출하지 않고, 필요한 입력만 받기 위해 따로 만든다.
 *
 * record = 값을 담기만 하는 클래스를 짧게 정의하는 자바 문법.
 *
 * 각 어노테이션은 입력값 검증 규칙:
 *   @Email    : 이메일 형식이어야 함
 *   @NotBlank : 비어 있으면 안 됨
 *   @Size     : 길이 제한
 */
public record SignupRequest(

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String fullName,

        String nationality,

        /** 성별 (선택, "male" / "female" / "other") */
        String gender,

        /** 공개 핸들(@아이디, 선택). 비어 있으면 이메일 로컬파트로 폴백. */
        String nickname
) {
}
