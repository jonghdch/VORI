package com.vori.backend.user.dto;

import java.time.LocalDateTime;

/**
 * 탈퇴 접수 결과.
 *
 * @param deleteAt 이 시각이 지나면 영구 삭제된다. 그 전에 로그인하면 복구.
 */
public record WithdrawalResponse(LocalDateTime deleteAt) {}
