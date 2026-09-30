package com.vori.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Google Identity Services 버튼 콜백이 준 credential(ID 토큰 JWT). */
public record GoogleLoginRequest(
    @NotBlank(message = "구글 인증 정보가 필요합니다")
    String credential
) {}
