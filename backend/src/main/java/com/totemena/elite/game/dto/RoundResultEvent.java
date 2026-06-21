package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundResultEvent {
    private String type = "ROUND_RESULT";
    private String roundId;
    private int winningRowTota;
    private int winningRowMena;

    public RoundResultEvent(String roundId, int winningRowTota, int winningRowMena) {
        this.roundId = roundId;
        this.winningRowTota = winningRowTota;
        this.winningRowMena = winningRowMena;
    }
}
