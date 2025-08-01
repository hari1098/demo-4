package com.example.demo.ticket;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Getter
@Setter
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId; // New field to link to a betting session
    private Integer ticketId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    private LocalDateTime time;

    public Bet(String sessionId, Integer ticketId, BigDecimal amount, LocalDateTime time) {
        this.sessionId = sessionId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.time = time;
    }
}