package com.guidematch.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByPortoneUid(String portoneUid);
    Optional<Payment> findByMerchantUid(String merchantUid);
    boolean existsByPortoneUid(String portoneUid);
}
