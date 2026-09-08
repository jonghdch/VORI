package com.vori.backend.common;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * 에러 응답 본문.
 *
 * Spring Boot 기본 형식(timestamp/status/error/path)에 message 를 더한 모양이다.
 * 필드를 새로 만들지 않고 기본 형식을 따른 이유는, 이 핸들러가 잡지 못하는 예외
 * (Security 필터의 401 등)는 여전히 기본 형식으로 나가기 때문이다 — 프론트가
 * 두 가지 모양을 구분해서 다룰 필요가 없어야 한다.
 *
 * message 는 사용자에게 그대로 보여줄 수 있는 한글 문구다. 서버 내부 사정을 담지 않는다.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path);
    }
}
