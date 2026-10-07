package com.vori.backend.admin.dto;

/** 「AI 사용량」 스위치 변경. null 인 칸은 그대로 둔다. */
public record AiSettingsUpdateRequest(Boolean questionWording, Boolean dailyComment) {}
