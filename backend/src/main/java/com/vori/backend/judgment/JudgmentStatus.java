package com.vori.backend.judgment;

/**
 * 하루 신호등 판정 단계 (docs/judgment-flow.md).
 * PENDING — 1차 신호등 판정만 끝났고 예외 지출 사유를 기다리는 중.
 * FINALIZED — 사유 입력(또는 건너뛰기·자정)까지 끝나 최종 신호등 판정과 보상이 정해졌다.
 * 보상 지급은 상태와 별개로 그날 자정에 한 번 한다(DailyJudgment.rewardedAt).
 */
public enum JudgmentStatus { PENDING, FINALIZED }
