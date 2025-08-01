package com.example.demo.ticket;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/bets")
@CrossOrigin("*")
public class BetController {

    private final BetService betService;
    private final BettingSessionManager bettingSessionManager; // Inject BettingSessionManager

    public BetController(BetService betService, BettingSessionManager bettingSessionManager) {
        this.betService = betService;
        this.bettingSessionManager = bettingSessionManager;
    }

    // Endpoint to start a new betting session
    @PostMapping("/session/start")
    public ResponseEntity<BettingSession> startBettingSession(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "5") long durationMinutes,
            @RequestParam(defaultValue = "15") long taskIntervalSeconds) {
        BettingSession session = bettingSessionManager.startNewSession(sessionId, durationMinutes, taskIntervalSeconds);
        if (session != null) {
            return new ResponseEntity<>(session, HttpStatus.CREATED);
        }
        return new ResponseEntity<>(HttpStatus.CONFLICT); // Session with this ID already exists
    }

    // Endpoint to get the status of a betting session
    @GetMapping("/session/status/{sessionId}")
    public ResponseEntity<BettingSession> getSessionStatus(@PathVariable String sessionId) {
        BettingSession session = bettingSessionManager.getSession(sessionId);
        if (session != null) {
            return new ResponseEntity<>(session, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // Endpoint to manually stop a betting session
    @PostMapping("/session/stop/{sessionId}")
    public ResponseEntity<Void> stopBettingSession(@PathVariable String sessionId) {
        if (bettingSessionManager.isSessionActive(sessionId)) {
            bettingSessionManager.stopSession(sessionId);
            return new ResponseEntity<>(HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // Place bet for a specific session
    @PostMapping("/place/{sessionId}") // Include sessionId in the URL path
    public ResponseEntity<Bet> placeBet(@PathVariable String sessionId, @RequestBody Bet bet) {
        if (!bettingSessionManager.isSessionActive(sessionId)) {
            // Return an error response if the specific betting session is closed
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        // Pass sessionId, ticketId, and amount to the service
        Bet newBet = betService.placeBet(sessionId, bet.getTicketId(), bet.getAmount());
        return new ResponseEntity<>(newBet, HttpStatus.OK);
    }

    // Get all bets for a specific session
    @GetMapping("/all/{sessionId}")
    public List<Bet> getAllBetsForSession(@PathVariable String sessionId) {
        return betService.getAllBetsForSession(sessionId);
    }

    // Get highest bet for a specific session
    @GetMapping("/highest/{sessionId}")
    public Optional<Bet> getHighestBetForSession(@PathVariable String sessionId) {
        return betService.getHighestBetForSession(sessionId);
    }

    // Get lowest bet for a specific session
    @GetMapping("/lowest/{sessionId}")
    public Optional<Bet> getLowestBetForSession(@PathVariable String sessionId) {
        return betService.getLowestBetForSession(sessionId);
    }
}