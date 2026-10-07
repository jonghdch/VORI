package com.vori.backend.gemini;

import lombok.Getter;

/**
 * Gemini 한도에 걸렸다 — 주 모델·대체 모델이 모두 429.
 *
 * <p>다른 실패(과부하·타임아웃·키 문제)와 나눠 두는 이유는 안내가 달라야 해서다. 다른 실패는 「잠시 후 다시」가
 * 맞지만, 하루 한도는 태평양 자정(한국 16~17시)까지 몇 번을 다시 눌러도 안 된다. 11/12 게스트 시연에서
 * 방문자가 「고장 났다」고 보지 않게, 언제 다시 되는지 알려 준다.
 */
@Getter
public class AiQuotaException extends RuntimeException {

    public enum Kind {
        /** 하루 한도 — 리셋 시각까지 안 된다. */
        DAILY,
        /** 분당 한도 등 — 잠깐 뒤면 풀린다. */
        PER_MINUTE
    }

    private final Kind kind;
    /** DAILY 일 때 다시 쓸 수 있는 때(예: 「오후 5시」「내일 오후 5시」). PER_MINUTE 면 null. */
    private final String availableAt;

    public AiQuotaException(Kind kind, String availableAt) {
        super(kind == Kind.DAILY ? "AI 하루 사용량 소진" : "AI 요청 몰림");
        this.kind = kind;
        this.availableAt = availableAt;
    }

    public boolean isDaily() {
        return kind == Kind.DAILY;
    }
}
