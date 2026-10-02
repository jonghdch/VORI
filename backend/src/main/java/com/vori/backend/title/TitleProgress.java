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
        long receiptScans,    // 인식에 성공한 영수증 수
        long loginCount,      // 누적 로그인 횟수
        long petInteractions, // 가장 많이 상호작용한 펫의 상호작용 횟수 (업적 사랑둥이는 펫 칭호로 옮겨 지금은 안 씀)
        long petsHatched,     // 부화한(받은) 펫 수, 시작 펫 포함
        long speciesGraduated,// 졸업시킨 서로 다른 종 수
        long petTitlesTotal,  // 모든 펫이 딴 칭호 합계
        long petTitleKinds,   // 한 번이라도 딴 서로 다른 공개 펫 칭호 수
        long petTitlesOnOnePet, // 한 펫이 딴 칭호 최대 개수
        long hiddenPetTitles  // 딴 히든 펫 칭호 수
) {}
