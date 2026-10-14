package com.vori.backend.auth;

import com.vori.backend.auth.dto.SignupRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 400 의 message 는 가입 화면에 그대로 뜬다. 어느 칸이 틀렸는지 알 수 있는 한국어 문구가 나와야 한다 —
     * 검증기 기본 문구는 칸 이름이 없고 요청 언어에 따라 영어로 나간다.
     */
    @Test
    void eachFieldGivesItsOwnMessage() {
        assertEquals(Set.of("이메일 형식을 확인해 주세요"), messages(
                new SignupRequest("vori.com", "pw123!!!", "닉네임", "홍길동", true, true, false)));
        assertEquals(Set.of("닉네임은 2~12자로 입력해 주세요"), messages(req("a", "홍길동")));
        assertEquals(Set.of("이름은 2~30자로 입력해 주세요"), messages(req("닉네임", "홍")));
        assertEquals(Set.of("비밀번호는 8자 이상이며 특수문자를 1개 이상 포함해야 합니다"), messages(
                new SignupRequest("a@vori.com", "password", "닉네임", "홍길동", true, true, false)));
    }

    private Set<String> messages(SignupRequest req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
