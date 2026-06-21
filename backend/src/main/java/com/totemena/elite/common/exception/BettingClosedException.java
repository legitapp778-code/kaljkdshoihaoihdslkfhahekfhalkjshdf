package com.totemena.elite.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class BettingClosedException extends RuntimeException {
    public BettingClosedException(String message) {
        super(message);
    }
}
