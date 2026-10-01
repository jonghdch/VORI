package com.vori.backend.notification;

/** 알림 종류. 화면이 아이콘·문구를 고를 때 쓴다. */
public enum NotificationType {
    /** 월간(보이는) 리포트 정산 도착 */
    MONTHLY_REPORT,
    /** 새 칭호 획득 */
    TITLE_ACQUIRED,
    /** 펫 진화(2차·3차) */
    PET_EVOLVED,
    /** 펫 30레벨 — 졸업(분양) 가능 */
    PET_GRADUATE_READY,
    /** 오늘 소비 판정을 할 수 있음(20시) */
    JUDGMENT_OPEN
}
