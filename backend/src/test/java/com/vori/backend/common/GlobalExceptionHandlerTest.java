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
        assertEquals("입력 형식이 올바르지 않아요. 숫자 칸과 값의 범위를 확인해 주세요.", res.getBody().message());
        assertFalse(res.getBody().message().contains("JSON"), "내부 파싱 메시지를 내보내지 않는다");
    }
}
