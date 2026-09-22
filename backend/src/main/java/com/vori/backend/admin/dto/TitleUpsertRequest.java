package com.vori.backend.admin.dto;

import com.vori.backend.title.TitleMetricType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 칭호 생성·수정 요청. 수정 시 code 는 무시된다(생성 시에만 쓰임).
 * code 는 대문자·숫자·밑줄만 — 시드(V11)와 같은 규칙이라 로그·JSON 에서 그대로 읽힌다.
 */
public record TitleUpsertRequest(
        @Size(max = 50, message = "코드는 50자 이내여야 합니다.")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "코드는 대문자·숫자·밑줄만 쓸 수 있습니다. (예: SAVER_SPROUT)")
        String code,

        @NotBlank(message = "칭호 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
        String name,

        @NotBlank(message = "조건 설명을 입력해주세요.")
        @Size(max = 200, message = "설명은 200자 이내여야 합니다.")
        String description,

        @NotNull(message = "판정 지표를 선택해주세요.")
        TitleMetricType metricType,

        @NotNull(message = "목표치를 입력해주세요.")
        @Min(value = 1, message = "목표치는 1 이상이어야 합니다.")
        Long threshold,

        Boolean enabled,

        @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
        Integer sortOrder
) {}
