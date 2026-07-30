package com.leonid.giwaapi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String userName,
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Business number must contain exactly 10 digits")
        String businessNumber
) {
}
