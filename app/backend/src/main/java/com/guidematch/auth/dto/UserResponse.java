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
        String nationality,
        String mbti,
        java.util.List<String> interests
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getNationality(),
                user.getMbti(),
                user.getInterestList()
        );
    }
}
