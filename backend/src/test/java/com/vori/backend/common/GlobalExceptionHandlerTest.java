package com.vori.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 본문을 읽지 못한 요청(숫자 칸에 문자열, int 범위 초과 등)도 문구를 실어야 한다.
 * 재현: PUT /api/users/me 에 monthlyIncome=3000000000 → message 없는 400 이라
 * 화면에는 "요청을 처리할 수 없어요" 만 떴다.
 */
class GlobalExceptionHandlerTest {

    @Test
    void unreadableBodyGetsMessage() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error: Numeric value out of range of int", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> res = new GlobalExceptionHandler()
                .handleUnreadable(e, new MockHttpServletRequest("PUT", "/api/users/me"));

        assertEquals(400, res.getStatusCode().value());
        assertEquals("입력 형식이 올바르지 않아요. 입력값을 확인해 주세요.", res.getBody().message());
        assertFalse(res.getBody().message().contains("JSON"), "내부 파싱 메시지를 내보내지 않는다");
    }

    private final MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/expenses");

    @Test
    void duplicateKeyIs409() {
        var e = new org.springframework.dao.DataIntegrityViolationException("dup",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry '1-2026-10-19'", "23000", 1062));
        assertEquals(409, new GlobalExceptionHandler().handleDataIntegrity(e, post).getStatusCode().value());
    }

    @Test
    void otherConstraintViolationIsNot409() {
        // 글자 수 초과(1406)·FK(1452)는 중복이 아니다 — "이미 처리됨" 으로 감추지 않는다
        var tooLong = new org.springframework.dao.DataIntegrityViolationException("long",
                new java.sql.SQLException("Data too long for column 'memo'", "22001", 1406));
        var fk = new org.springframework.dao.DataIntegrityViolationException("fk",
                new java.sql.SQLIntegrityConstraintViolationException("Cannot add or update a child row", "23000", 1452));
        assertEquals(500, new GlobalExceptionHandler().handleDataIntegrity(tooLong, post).getStatusCode().value());
        assertEquals(500, new GlobalExceptionHandler().handleDataIntegrity(fk, post).getStatusCode().value());
    }

    @Test
    void emptyOptionalIs404ButOtherNoSuchElementIs500() {
        var empty = assertThrows(java.util.NoSuchElementException.class, () -> java.util.Optional.empty().orElseThrow());
        var iter = assertThrows(java.util.NoSuchElementException.class, () -> java.util.List.of().iterator().next());
        assertEquals(404, new GlobalExceptionHandler().handleNoSuchElement(empty, post).getStatusCode().value());
        assertEquals(500, new GlobalExceptionHandler().handleNoSuchElement(iter, post).getStatusCode().value());
    }
}
