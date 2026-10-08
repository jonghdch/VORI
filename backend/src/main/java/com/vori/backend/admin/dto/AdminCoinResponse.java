package com.vori.backend.admin.dto;

/** 관리자 코인 지급 결과. */
public record AdminCoinResponse(Long userId, Integer gameMoney, Integer granted) {}
