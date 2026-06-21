package com.totemena.elite.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEvent {
    private String type = "ERROR";
    private String code;
    private String message;

    public ErrorEvent(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
