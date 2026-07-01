package com.guidematch.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 한 예약의 메시지를 오래된 순으로 (대화 흐름대로)
    List<Message> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
