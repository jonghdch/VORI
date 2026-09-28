package com.vori.backend.auth;

import com.vori.backend.auth.dto.SignupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 가입도 프로필 수정(ProfileUpdateRequest)처럼 공백을 뺀 값으로 길이를 검사하고 저장한다. */
class SignupRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SignupRequest req(String nickname, String name) {
        return new SignupRequest("a@vori.com", "pw123!!!", nickname, name, true, true, false);
    }

    @Test
    void lengthIsCheckedAfterTrimming() {
        assertFalse(validator.validate(req("a ", "홍길동")).isEmpty());
        assertFalse(validator.validate(req("닉네임", " 홍")).isEmpty());
        assertTrue(validator.validate(req(" 닉네임 ", " 홍길동 ")).isEmpty());
        assertEquals("닉네임", req(" 닉네임 ", "홍길동").nickname());
        assertEquals("홍길동", req("닉네임", " 홍길동 ").name());
    }
}
