package com.vori.backend.title;

/**
 * 칭호 조건 평가에 필요한 사용자 지표 묶음.
 *
 * 조건마다 개별 쿼리를 던지면 칭호 수만큼 DB 를 왕복하게 되므로, 한 번에 모아 읽고
 * 그 값으로 모든 조건을 판정한다. 지표를 추가할 때는 여기와 TitleService.collect() 만 손대면 된다.
 */
public record TitleProgress(
        long totalSaved,      // 누적 절약액(원)
        long expenseCount,    // 지출 등록 건수
        long goalsAchieved,   // 달성한 절약 목표 수
        long petsReleased,    // 분양한 펫 수
        long sTierPets,       // 뽑은 S 등급 펫 수
        long aiAnswers,       // 답변을 마친 AI 질문 수
        long receiptScans     // 인식에 성공한 영수증 수
) {}
