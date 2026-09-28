package com.vori.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 환경설정에서 수정하는 본인 프로필. 이메일과 비밀번호는 별도 인증 흐름에서 관리한다. */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요")
        @Size(max = 30, message = "닉네임은 30자 이내로 입력해 주세요")
        String nickname,

        @Size(max = 30, message = "이름은 30자 이내로 입력해 주세요")
        String name,

        @Min(value = 1, message = "나이는 1 이상으로 입력해 주세요")
        @Max(value = 120, message = "나이는 120 이하로 입력해 주세요")
        Integer age,

        @Size(max = 50, message = "직업은 50자 이내로 입력해 주세요")
        String job,

        @Min(value = 0, message = "월 수입은 0 이상으로 입력해 주세요")
        Integer monthlyIncome
) {}
