package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundTickEvent {
    private String type = "TICK";
    private String roundId;
    private String phase;
    private int secondsRemaining;

    public RoundTickEvent(String roundId, String phase, int secondsRemaining) {
        this.roundId = roundId;
        this.phase = phase;
        this.secondsRemaining = secondsRemaining;
    }
}
