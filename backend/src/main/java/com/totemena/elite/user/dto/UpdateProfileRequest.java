package com.totemena.elite.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 .'-]+$",
             message = "Name can only contain letters, numbers, spaces, dots, apostrophes and hyphens")
    private String displayName;
}
