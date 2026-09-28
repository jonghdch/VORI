package com.vori.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 환경설정에서 수정하는 본인 프로필. 이메일과 비밀번호는 별도 인증 흐름에서 관리한다.
 * 닉네임·이름 규칙은 가입(SignupRequest)과 같다 — 다르면 수정으로 가입 규칙을 우회하게 된다.
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요")
        @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해 주세요")
        String nickname,

        @NotBlank(message = "이름을 입력해 주세요")
        @Size(min = 2, max = 30, message = "이름은 2~30자로 입력해 주세요")
        String name,

        @Min(value = 1, message = "나이는 1 이상으로 입력해 주세요")
        @Max(value = 120, message = "나이는 120 이하로 입력해 주세요")
        Integer age,

        @Size(max = 50, message = "직업은 50자 이내로 입력해 주세요")
        String job,

        @Min(value = 0, message = "월 수입은 0 이상으로 입력해 주세요")
        Integer monthlyIncome
) {
    /** 공백을 먼저 잘라 둔다. 검사(@Size)와 저장이 같은 값을 봐야 "a " 가 2자로 통과한 뒤 1자로 저장되지 않는다. */
    public ProfileUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
        name = name == null ? null : name.trim();
        job = job == null || job.isBlank() ? null : job.trim();
    }
}
