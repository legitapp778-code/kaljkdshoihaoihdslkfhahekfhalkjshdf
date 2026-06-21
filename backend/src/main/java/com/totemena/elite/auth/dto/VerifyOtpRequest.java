package com.totemena.elite.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Phone number must be 10 digits")
    private String phone;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{4}$", message = "OTP must be 4 digits")
    private String otp;

    private String displayName;
    
    private String email;
}
