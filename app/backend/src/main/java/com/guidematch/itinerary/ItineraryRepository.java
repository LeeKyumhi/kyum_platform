package com.guidematch.itinerary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
