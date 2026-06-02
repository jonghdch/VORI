package com.vori.backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Email @Size(max = 100)
    String email,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?]).{8,}$",
        message = "비밀번호는 8자 이상이며 특수문자를 1개 이상 포함해야 합니다"
    )
    String password,

    @NotBlank @Size(min = 2, max = 12)
    String nickname,

    @NotBlank @Size(min = 2, max = 30)
    String name,

    @AssertTrue(message = "이용약관 동의가 필요합니다")
    Boolean termsAgreed,

    @AssertTrue(message = "개인정보 수집·이용 동의가 필요합니다")
    Boolean privacyAgreed,

    Boolean marketingAgreed
) {
}
