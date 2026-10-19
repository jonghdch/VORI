package com.vori.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러·서비스가 흔히 흘리는 예외가 message 없는 500 이 아니라 뜻에 맞는 상태로 나간다.
 * 재현: GET /api/attendance?month=2026-13 → YearMonth.parse 실패가 500 이었다.
 */
class UnhandledExceptionStatusTest {

    @RestController
    static class Probe {
        @GetMapping("/month") String month(@RequestParam String month) { return YearMonth.parse(month).toString(); }
        @GetMapping("/missing") String missing() { return Optional.<String>empty().orElseThrow(); }
        @GetMapping("/duplicate") String duplicate() { throw new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'uq_secret_index'", "23000", 1062)); }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void badMonthIs400() throws Exception {
        mvc.perform(get("/month").param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("날짜 형식이 올바르지 않아요."));
    }

    @Test
    void emptyOptionalIs404() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound());
    }

    @Test
    void uniqueConflictIs409WithoutDbDetail() throws Exception {
        mvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리된 요청이에요. 새로고침 후 다시 확인해 주세요."));
    }
}
