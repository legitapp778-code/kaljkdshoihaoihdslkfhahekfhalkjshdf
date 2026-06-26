package com.totemena.elite.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateTicketRequest {
    @NotBlank(message = "Subject is required")
    @Size(min = 5, max = 200, message = "Subject must be between 5 and 200 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 .,!?'\"()-]+$",
             message = "Subject contains invalid characters")
    private String subject;
}
