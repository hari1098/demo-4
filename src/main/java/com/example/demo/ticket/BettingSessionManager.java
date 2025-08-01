package com.example.demo.ticket;

import jakarta.annotation.PostConstruct; // Keep this for startup initialization
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class BettingSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BettingSessionManager.class);
    private final ThreadPoolTaskScheduler taskScheduler;
    private final BetService betService;
    private final BettingSessionRepository bettingSessionRepository; // NEW: Inject BettingSessionRepository

    // This map now holds only the *currently scheduled* tasks (runtime state) for easy cancellation.
    // The persistent state (active/inactive) is in the database.
    private final Map<String, BettingSession> scheduledSessionsRuntimeMap;

    public BettingSessionManager(BetService betService, BettingSessionRepository bettingSessionRepository) {
        this.betService = betService;
        this.bettingSessionRepository = bettingSessionRepository; // Initialize the new repository
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(Runtime.getRuntime().availableProcessors() * 2); // Increased pool size for multiple sessions
        this.taskScheduler.setThreadNamePrefix("BettingSessionScheduler-");
        this.taskScheduler.initialize();
        this.scheduledSessionsRuntimeMap = new ConcurrentHashMap<>();
    }

    // --- NEW: @PostConstruct method to load and re-schedule active sessions from DB on startup ---
    @PostConstruct
    public void initializeScheduledSessions() {
        log.info("Initializing BettingSessionManager: Loading active sessions from database...");
        // Find all sessions that were marked as active in the DB
        bettingSessionRepository.findByActiveTrue().forEach(session -> {
            // Check if the session has already logically expired while the server was down
            if (session.getStartTime().plus(Duration.ofMinutes(session.getDurationMinutes())).isBefore(Instant.now())) {
                log.info("Session '{}' found in DB but already expired. Marking as inactive and stopping.", session.getSessionId());
                session.setActive(false); // Mark as inactive
                bettingSessionRepository.save(session); // Persist status change to DB
                // No need to schedule tasks for an already expired session
            } else {
                log.info("Re-scheduling active session '{}' (remaining time: {} minutes).",
                        session.getSessionId(),
                        Duration.between(Instant.now(), session.getStartTime().plus(Duration.ofMinutes(session.getDurationMinutes()))).toMinutes());

                // Schedule the periodic task (e.g., to log highest/lowest bets)
                ScheduledFuture<?> periodicTask = taskScheduler.scheduleAtFixedRate(() -> {
                    runSessionTask(session.getSessionId());
                }, Duration.ofSeconds(15)); // Using a default 15s interval for re-scheduled tasks.
                // You might want to store this interval in BettingSession if it varies.

                session.setScheduledFuture(periodicTask); // Store future reference in the transient field
                scheduledSessionsRuntimeMap.put(session.getSessionId(), session); // Add to runtime map

                // Schedule the one-time termination task
                taskScheduler.schedule(() -> {
                    stopSession(session.getSessionId());
                }, session.getStartTime().plus(Duration.ofMinutes(session.getDurationMinutes())));
            }
        });
        log.info("BettingSessionManager initialized. {} active sessions re-scheduled from DB.", scheduledSessionsRuntimeMap.size());
    }


    public BettingSession startNewSession(String sessionId, long durationMinutes, long taskIntervalSeconds) {
        // First, check if a session with this ID already exists in the database (active or inactive)
        if (bettingSessionRepository.existsById(sessionId)) {
            log.warn("Session with ID '{}' already exists in the database. Cannot start a new one.", sessionId);
            return null; // Indicates a conflict
        }

        // Create a new BettingSession object
        BettingSession session = new BettingSession(sessionId, durationMinutes);
        session.setActive(true); // Mark it as active
        bettingSessionRepository.save(session); // Persist the new session to the database

        log.info("Starting new betting session '{}'. It will run for {} minutes.", sessionId, durationMinutes);

        // Schedule the periodic task for this session
        ScheduledFuture<?> periodicTask = taskScheduler.scheduleAtFixedRate(() -> {
            runSessionTask(sessionId);
        }, Duration.ofSeconds(taskIntervalSeconds));
        session.setScheduledFuture(periodicTask); // Store the ScheduledFuture in the transient field

        // Schedule the one-time task to stop the session after its duration
        taskScheduler.schedule(() -> {
            stopSession(sessionId);
        }, Instant.now().plus(Duration.ofMinutes(durationMinutes)));

        // Add to the runtime map for managing scheduled tasks
        scheduledSessionsRuntimeMap.put(sessionId, session);
        return session;
    }

    private void runSessionTask(String sessionId) {
        // Get the session from the runtime map (where its ScheduledFuture is stored)
        BettingSession session = scheduledSessionsRuntimeMap.get(sessionId);
        if (session != null && session.isActive()) {
            log.info("--- Session '{}' running... ---", sessionId);
            betService.findAndLogHighestAndLowestForSession(sessionId);
        }
    }

    public void stopSession(String sessionId) {
        // Remove from the runtime map first, to prevent new tasks for it
        BettingSession session = scheduledSessionsRuntimeMap.remove(sessionId);

        // Update the database record to mark it inactive
        bettingSessionRepository.findById(sessionId).ifPresent(dbSession -> {
            if (dbSession.isActive()) { // Only log if it was actually active before
                dbSession.setActive(false);
                bettingSessionRepository.save(dbSession);
                log.info("Betting session '{}' marked as inactive in DB.", sessionId);
            }
        });

        // Cancel the periodic scheduled task if it was in the runtime map
        if (session != null && session.getScheduledFuture() != null) {
            session.getScheduledFuture().cancel(false);
            log.info("Scheduled task for session '{}' cancelled.", sessionId);
        } else {
            // This might happen if stopSession is called on a session that already expired
            // and was thus removed from scheduledSessionsRuntimeMap.
            // Or if another server instance stopped it.
            log.warn("Attempted to stop session '{}' not found in runtime map or already inactive.", sessionId);
        }
    }

    // Updated: Now checks database as the primary source of truth for session activity
    public boolean isSessionActive(String sessionId) {
        Optional<BettingSession> dbSession = bettingSessionRepository.findById(sessionId);
        return dbSession.isPresent() && dbSession.get().isActive();
    }

    // Updated: Now retrieves session details from the database
    public BettingSession getSession(String sessionId) {
        return bettingSessionRepository.findById(sessionId).orElse(null);
    }

    @PreDestroy
    public void shutdown() {
        // Cancel all currently scheduled tasks
        scheduledSessionsRuntimeMap.values().forEach(session -> {
            if (session.getScheduledFuture() != null) {
                session.getScheduledFuture().cancel(false);
            }
        });
        taskScheduler.shutdown();
        log.info("Betting session manager has been shut down.");
    }
}