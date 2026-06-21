package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BetAckEvent {
    private String type = "BET_ACK";
    private String betId;
    private String bird;
    private int selectedRow;
    private long amountPaise;
    private long balanceAfterPaise;
    private String idempotencyKey;

    public BetAckEvent(String betId, String bird, int selectedRow, long amountPaise, long balanceAfterPaise,
            String idempotencyKey) {
        this.betId = betId;
        this.bird = bird;
        this.selectedRow = selectedRow;
        this.amountPaise = amountPaise;
        this.balanceAfterPaise = balanceAfterPaise;
        this.idempotencyKey = idempotencyKey;
    }
}
