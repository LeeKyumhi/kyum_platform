package com.guidematch.guide.dto;

import com.guidematch.guide.CredentialType;
import com.guidematch.guide.GuideCredential;

import java.time.Instant;

public record CredentialResponse(
        Long id,
        CredentialType type,
        String title,
        String fileUrl,
        Instant createdAt
) {
    public static CredentialResponse from(GuideCredential c) {
        return new CredentialResponse(
                c.getId(),
                c.getType(),
                c.getTitle(),
                c.getFileUrl(),
                c.getCreatedAt()
        );
    }
}
