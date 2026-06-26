package com.totemena.elite.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class PlaceBetRequest {
    @NotBlank
    @Pattern(regexp = "^(tota|mena)$", message = "Bird must be either 'tota' or 'mena'")
    private String bird;

    @Min(1)
    @Max(5)
    private short selectedRow;

    @Min(value = 100, message = "Minimum bet is ₹1 (100 paise)")
    @Max(value = 10_000_000L, message = "Maximum bet is ₹1,00,000 (10000000 paise)")
    private long amountPaise;

    @NotNull
    private UUID idempotencyKey;
}
