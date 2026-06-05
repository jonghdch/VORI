package com.vori.backend.sanction.dto;

import com.vori.backend.sanction.SanctionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제재 생성 요청. type=SUSPENSION 이면 durationDays(정지 기간) 필요 — 서비스에서 검증.
 */
public record SanctionCreateRequest(
        @NotNull(message = "대상 유저를 선택해주세요.")
        Long userId,

        @NotNull(message = "제재 종류를 선택해주세요.")
        SanctionType type,

        @NotBlank(message = "사유를 입력해주세요.")
        @Size(max = 255, message = "사유는 최대 255자입니다.")
        String reason,

        Integer durationDays
) {}
