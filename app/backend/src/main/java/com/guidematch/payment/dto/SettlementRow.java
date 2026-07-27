package com.guidematch.payment.dto;

import java.time.Instant;

public record SettlementRow(
        Long id, Long bookingId, Long guideProfileId,
        Integer grossAmount, Integer commissionAmount, Integer netAmount,
        String status, Instant createdAt, Instant paidOutAt, String adminMemo) {}
