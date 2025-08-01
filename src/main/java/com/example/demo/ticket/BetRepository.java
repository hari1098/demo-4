package com.example.demo.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    Optional<Bet> findTopBySessionIdOrderByAmountDesc(String sessionId);

    Optional<Bet> findTopBySessionIdOrderByAmountAsc(String sessionId);

    List<Bet> findBySessionId(String sessionId);

    // --- Add or ensure this method is present ---
    // Finds a bet with this sessionId and ticketId
    Optional<Bet> findBySessionIdAndTicketId(String sessionId, Integer ticketId);

    // This method might or might not be needed depending on the 'unique amount' rule
    // boolean existsBySessionIdAndAmount(String sessionId, BigDecimal amount);
}