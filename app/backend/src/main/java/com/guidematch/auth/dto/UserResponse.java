package com.guidematch.auth.dto;

import com.guidematch.user.User;

/**
 * 클라이언트에게 돌려줄 사용자 정보 DTO.
 * 중요한 점: 비밀번호(password)는 절대 포함하지 않는다.
 * 엔티티를 그대로 반환하면 비밀번호 해시까지 노출되므로 별도 DTO로 걸러서 보낸다.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String nickname,
        String handle,
        String nationality,
        String city,
        Double latitude,
        Double longitude,
        String gender,
        String mbti,
        java.util.List<String> interests,
        boolean emailVerified,
        /**
         * 결제용 연락처(E.164). 이 DTO는 본인 조회 전용(signup 응답 · GET /me · 본인 수정 응답)이라
         * 여기 담아도 상대방에게 가지 않는다 — 타인에게 나가는 정보는 전부 별도 DTO를 쓴다.
         */
        String phone
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getNickname(),
                user.getHandle(),
                user.getNationality(),
                user.getCity(),
                user.getLatitude(),
                user.getLongitude(),
                user.getGender(),
                user.getMbti(),
                user.getInterestList(),
                user.isEmailVerified(),
                user.getPhone()
        );
    }
}
