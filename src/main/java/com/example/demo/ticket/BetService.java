package com.example.demo.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BetService {

    private static final Logger log = LoggerFactory.getLogger(BetService.class);
    private final BetRepository betRepository;

    public BetService(BetRepository betRepository) {
        this.betRepository = betRepository;
    }

    public Bet placeBet(String sessionId, Integer ticketId, BigDecimal amount) {

        // --- Rule 1: Overall Ascending Bids (Strictly Greater than Current Highest) ---
        // Get the current overall highest bet for the session
        Optional<Bet> overallHighestBetOptional = getHighestBetForSession(sessionId);
        if (overallHighestBetOptional.isPresent()) {
            BigDecimal currentOverallHighestAmount = overallHighestBetOptional.get().getAmount();
            // New bet must be strictly greater than the overall highest bet in the session
            if (amount.compareTo(currentOverallHighestAmount) <= 0) {
                log.warn("Betting Rule Violation: New amount ₹{} is not strictly greater than the overall highest bet ₹{} in session {}.", amount, currentOverallHighestAmount, sessionId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New bet amount must be strictly greater than the current overall highest bet of ₹" + currentOverallHighestAmount + ".");
            }
        }
        // If it's the very first bet in the session, Rule 1 doesn't apply (no overall highest yet).

        // --- Rule 2 (Clarified): Ticket ID's Own Bet Must Increase & Update Existing Bet ---
        Optional<Bet> existingBetByTicketIdOptional = betRepository.findBySessionIdAndTicketId(sessionId, ticketId);

        if (existingBetByTicketIdOptional.isPresent()) {
            // If this ticketId has placed a bet before
            BigDecimal previousBetAmountByThisTicket = existingBetByTicketIdOptional.get().getAmount();

            // New amount must be strictly greater than this ticket's previous bet
            if (amount.compareTo(previousBetAmountByThisTicket) <= 0) {
                log.warn("Betting Rule Violation: Ticket ID {} attempted to bet ₹{}, which is not strictly greater than their previous bet of ₹{} in session {}.", ticketId, amount, previousBetAmountByThisTicket, sessionId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your new bet amount must be strictly greater than your previous bet of ₹" + previousBetAmountByThisTicket + " in this session.");
            }

            // If all checks pass, update the existing bet for this ticket ID
            Bet existingBet = existingBetByTicketIdOptional.get();
            existingBet.setAmount(amount); // Update the amount
            existingBet.setTime(LocalDateTime.now()); // Update the timestamp
            log.info("Ticket ID {} updated their bet in session {}: New amount ₹{}", ticketId, sessionId, amount);
            return betRepository.save(existingBet); // Save the updated bet
        } else {
            // If this is the first time this ticketId is betting in this session,
            // and Rule 1 passed, create a new bet.
            Bet bet = new Bet(sessionId, ticketId, amount, LocalDateTime.now());
            log.info("New bet placed in session {}: Ticket {}, Amount ₹{}", sessionId, ticketId, amount);
            return betRepository.save(bet);
        }
    }

    // --- Existing methods, no changes needed here ---
    public List<Bet> getAllBetsForSession(String sessionId) {
        return betRepository.findBySessionId(sessionId);
    }

    public Optional<Bet> getHighestBetForSession(String sessionId) {
        return betRepository.findTopBySessionIdOrderByAmountDesc(sessionId);
    }

    public Optional<Bet> getLowestBetForSession(String sessionId) {
        return betRepository.findTopBySessionIdOrderByAmountAsc(sessionId);
    }

    public void findAndLogHighestAndLowestForSession(String sessionId) {
        getHighestBetForSession(sessionId).ifPresentOrElse(
                bet -> log.info("🏆 Session {}: Highest Bet Found -> Ticket ID: {}, Amount: ₹{}", sessionId, bet.getTicketId(), bet.getAmount()),
                () -> log.warn("Session {}: Could not determine highest bet. No bets found.", sessionId)
        );

        getLowestBetForSession(sessionId).ifPresentOrElse(
                bet -> log.info("📉 Session {}: Lowest Bet Found  -> Ticket ID: {}, Amount: ₹{}", sessionId, bet.getTicketId(), bet.getAmount()),
                () -> log.warn("Session {}: Could not determine lowest bet. No bets found.", sessionId)
        );
    }
}