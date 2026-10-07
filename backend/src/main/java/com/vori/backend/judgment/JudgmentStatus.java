package com.vori.backend.judgment;

/**
 * 하루 판정 단계 (docs/judgment-flow.md).
 * PENDING — 1차 판정만 끝났고 예외 지출 사유를 기다리는 중. 보상은 아직 주지 않았다.
 * FINALIZED — 사유 입력(또는 건너뛰기)까지 끝나 확정됐고 보상을 지급했다.
 */
public enum JudgmentStatus { PENDING, FINALIZED }
