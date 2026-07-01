package com.guidematch.booking;

/**
 * 예약 상태.
 *  REQUESTED  : 여행자가 요청함 (가이드 응답 대기)
 *  ACCEPTED   : 가이드가 수락 (계약 성립)
 *  REJECTED   : 가이드가 거절
 *  CANCELLED  : 여행자가 취소
 *  COMPLETED  : 일정 종료 (이후 리뷰 가능)
 */
public enum BookingStatus {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
