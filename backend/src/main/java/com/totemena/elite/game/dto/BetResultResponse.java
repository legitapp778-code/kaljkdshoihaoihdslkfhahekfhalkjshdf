package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetResultResponse {
    private UUID betId;
    private String bird;
    private short selectedRow;
    private long amountPaise;
    private long balanceAfterPaise;
}
