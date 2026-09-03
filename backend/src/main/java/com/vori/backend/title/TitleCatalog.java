package com.vori.backend.title;

import java.util.function.ToLongFunction;

/**
 * 획득 가능한 칭호 목록 (마스터 데이터 — 테이블 대신 코드로 관리).
 *
 * user_titles 는 획득한 칭호만 행으로 갖는 구조라 "아직 못 딴 칭호" 를 담을 테이블이 없다.
 * EggGrade·FurnitureCatalog 와 같은 방식으로 코드에 두고, 획득 시 이름을 복사해 저장한다.
 *
 * 각 칭호는 지표를 뽑는 함수(current)와 목표치(threshold)로 표현한다. 조건을 boolean 하나로
 * 두지 않은 이유는 미획득 칭호의 진행률(34/100만원)까지 화면에 내려주기 위해서다.
 */
public enum TitleCatalog {

    // ── 절약 ──
    SAVER_SPROUT("절약 새싹", "누적 절약 10만원", 100_000, TitleProgress::totalSaved),
    SAVER_MID("절약 중수", "누적 절약 50만원", 500_000, TitleProgress::totalSaved),
    SAVER_MASTER("절약 고수", "누적 절약 100만원", 1_000_000, TitleProgress::totalSaved),

    // ── 기록 ──
    RECORD_START("기록의 시작", "지출 10건 기록", 10, TitleProgress::expenseCount),
    RECORD_STEADY("꾸준한 기록가", "지출 50건 기록", 50, TitleProgress::expenseCount),
    RECORD_MASTER("기록 마스터", "지출 100건 기록", 100, TitleProgress::expenseCount),

    // ── 목표 ──
    GOAL_FIRST("목표 달성자", "절약 목표 1회 달성", 1, TitleProgress::goalsAchieved),
    GOAL_PLANNER("계획적인 소비자", "절약 목표 5회 달성", 5, TitleProgress::goalsAchieved),

    // ── 펫 ──
    PET_FIRST_RELEASE("첫 분양", "펫 1마리 분양", 1, TitleProgress::petsReleased),
    PET_COLLECTOR("펫 컬렉터", "펫 5마리 분양", 5, TitleProgress::petsReleased),

    // ── 가챠 ──
    LUCKY("행운아", "S등급 펫 획득", 1, TitleProgress::sTierPets),

    // ── AI ──
    TALKATIVE("소통왕", "AI 질문 10회 답변", 10, TitleProgress::aiAnswers),

    // ── 영수증 ──
    SCAN_MASTER("스캔 마스터", "영수증 10회 인식", 10, TitleProgress::receiptScans);

    private final String displayName;
    private final String description;
    private final long threshold;
    private final ToLongFunction<TitleProgress> current;

    TitleCatalog(String displayName, String description, long threshold,
                 ToLongFunction<TitleProgress> current) {
        this.displayName = displayName;
        this.description = description;
        this.threshold = threshold;
        this.current = current;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public long threshold() { return threshold; }

    public long currentOf(TitleProgress p) {
        return current.applyAsLong(p);
    }

    public boolean isAchieved(TitleProgress p) {
        return currentOf(p) >= threshold;
    }

    /** 진행률(%). 달성 후에도 100 을 넘기지 않는다 — 예산과 달리 초과를 표현할 이유가 없다. */
    public int progressPct(TitleProgress p) {
        if (threshold <= 0) return 100;
        return (int) Math.min(100, currentOf(p) * 100 / threshold);
    }
}
