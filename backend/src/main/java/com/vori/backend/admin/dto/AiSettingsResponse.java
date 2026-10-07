package com.vori.backend.admin.dto;

import com.vori.backend.gemini.GeminiClient;

import java.util.List;

/**
 * 관리자 화면 「AI 사용량」.
 *
 * @param questionWording 빨강 지출 질문 문구를 AI 로 만드는지(끄면 템플릿 문구)
 * @param dailyComment    일일 리포트의 펫 코멘트를 AI 로 만드는지(끄면 통계만)
 * @param models          주 모델부터 — 오늘 하루 한도를 다 썼는지, 다 썼으면 다시 쓸 수 있는 때(한국 시각)
 */
public record AiSettingsResponse(boolean questionWording, boolean dailyComment,
                                 List<GeminiClient.ModelQuota> models) {}
