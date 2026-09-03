package com.vori.backend.receipt;

public enum OcrProvider {
    /** 초기 설계상의 제공자. 현재는 쓰지 않지만 과거 행의 값을 위해 남겨둔다. */
    GOOGLE_VISION,
    /** 이미지에서 구조화 JSON 을 바로 받는다. 별도 KIE 파싱 단계가 필요 없다. */
    GEMINI
}
