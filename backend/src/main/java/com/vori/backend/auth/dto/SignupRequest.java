package com.vori.backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 제약마다 문구를 단다 — 400 응답의 message 가 가입 화면에 그대로 뜬다
 * (GlobalExceptionHandler). 문구가 없으면 검증기 기본 문구("크기가 2에서 12 사이여야 합니다")가
 * 어느 칸인지 없이 나가고, 요청 언어에 따라 영어로도 나간다. 닉네임·이름 문구는 ProfileUpdateRequest 와 같게.
 */
public record SignupRequest(
    @NotBlank(message = "이메일을 입력해 주세요")
    @Email(message = "이메일 형식을 확인해 주세요")
    @Size(max = 100, message = "이메일은 100자 이내로 입력해 주세요")
    String email,

    @NotBlank(message = "비밀번호를 입력해 주세요")
    @Pattern(
        regexp = "^(?=.*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?]).{8,}$",
        message = "비밀번호는 8자 이상이며 특수문자를 1개 이상 포함해야 합니다"
    )
    String password,

    @NotBlank(message = "닉네임을 입력해 주세요")
    @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해 주세요")
    String nickname,

    @NotBlank(message = "이름을 입력해 주세요")
    @Size(min = 2, max = 30, message = "이름은 2~30자로 입력해 주세요")
    String name,

    @AssertTrue(message = "이용약관 동의가 필요합니다")
    Boolean termsAgreed,

    @AssertTrue(message = "개인정보 수집·이용 동의가 필요합니다")
    Boolean privacyAgreed,

    Boolean marketingAgreed
) {
    /** 공백을 먼저 잘라 둔다. 길이 검사와 저장이 같은 값을 본다 (ProfileUpdateRequest 와 같은 규칙). */
    public SignupRequest {
        nickname = nickname == null ? null : nickname.trim();
        name = name == null ? null : name.trim();
    }
}
