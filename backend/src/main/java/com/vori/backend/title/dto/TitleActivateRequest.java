package com.vori.backend.title.dto;

/**
 * 칭호 장착 요청. titleId 가 null 이면 장착 해제 —
 * 해제를 위한 별도 엔드포인트를 두지 않으려는 의도적 설계다.
 */
public record TitleActivateRequest(Long titleId) {}
