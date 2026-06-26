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
    @Pattern(regexp = "^\\d{4,6}$", message = "OTP must be 4 to 6 digits")
    private String otp;

    private String displayName;
    
    private String email;

    @com.fasterxml.jackson.annotation.JsonProperty("isSignIn")
    private Boolean isSignIn;
}
