package com.vori.backend.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 500, message = "답변은 최대 500자까지 입력 가능합니다.")
        String answerText
) {}