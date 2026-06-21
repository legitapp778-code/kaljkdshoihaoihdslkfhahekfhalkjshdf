package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhaseChangeEvent {
    private String type = "PHASE_CHANGE";
    private String roundId;
    private String newPhase;
    private Instant timestamp;

    public PhaseChangeEvent(String roundId, String newPhase) {
        this.roundId = roundId;
        this.newPhase = newPhase;
        this.timestamp = Instant.now();
    }
}
