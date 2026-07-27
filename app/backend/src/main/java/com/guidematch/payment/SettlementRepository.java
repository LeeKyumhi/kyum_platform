package com.guidematch.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByBookingId(Long bookingId);
    List<Settlement> findByStatus(SettlementStatus status);
    List<Settlement> findAllByOrderByCreatedAtDesc();
}
