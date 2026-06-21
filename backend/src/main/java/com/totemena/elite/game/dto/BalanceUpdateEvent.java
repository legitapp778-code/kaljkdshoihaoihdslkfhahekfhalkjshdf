package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateEvent {
    private String type = "BALANCE_UPDATE";
    private long newBalancePaise;
    private String reason;
    private long amountPaise;
    private String roundId;
    private java.math.BigDecimal multiplier;

    public BalanceUpdateEvent(long newBalancePaise, String reason, long amountPaise, String roundId,
            java.math.BigDecimal multiplier) {
        this.newBalancePaise = newBalancePaise;
        this.reason = reason;
        this.amountPaise = amountPaise;
        this.roundId = roundId;
        this.multiplier = multiplier;
    }
}
