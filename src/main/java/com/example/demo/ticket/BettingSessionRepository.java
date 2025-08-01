package com.example.demo.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // Marks this interface as a Spring Data JPA repository
public interface BettingSessionRepository extends JpaRepository<BettingSession, String> {
    // JpaRepository provides basic CRUD operations (save, findById, findAll, delete, etc.)

    // Custom query method: Spring Data JPA automatically implements this based on method name
    // It will find all BettingSession entities where the 'active' field is true.
    List<BettingSession> findByActiveTrue();
}