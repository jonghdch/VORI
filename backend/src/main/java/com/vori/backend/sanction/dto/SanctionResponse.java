package com.vori.backend.sanction.dto;

import com.vori.backend.sanction.SanctionType;

import java.time.LocalDateTime;

/**
 * 제재 1건. status = ACTIVE(활성) / EXPIRED(만료) / LIFTED(해제) 파생.
 */
public record SanctionResponse(
        Long id,
        Long userId,
        String userNickname,
        String userEmail,
        SanctionType type,
        String reason,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime liftedAt
) {}
