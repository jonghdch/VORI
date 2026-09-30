package com.vori.backend.user;

import com.vori.backend.user.dto.ProfileUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 프로필 수정은 가입(SignupRequest)과 같은 규칙이어야 한다.
 * 재현: 가입은 닉네임 2~12자·이름 필수인데, 수정으로 1자·13자 닉네임과 빈 이름이 저장됐다.
 */
class ProfileUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String nickname, String name) {
        return validator.validate(new ProfileUpdateRequest(nickname, name, null, null, null)).isEmpty();
    }

    @Test
    void nicknameFollowsSignupLength() {
        assertFalse(valid("a", "홍길동"));
        assertTrue(valid("ab", "홍길동"));
        assertTrue(valid("가".repeat(12), "홍길동"));
        assertFalse(valid("가".repeat(13), "홍길동"));
    }

    /** 검사는 공백을 뺀 값으로 해야 한다. "a " 가 2자로 통과한 뒤 "a" 로 저장되면 안 된다. */
    @Test
    void lengthIsCheckedAfterTrimming() {
        assertFalse(valid("a ", "홍길동"));
        assertFalse(valid("닉네임", " 홍"));
        assertEquals("닉네임", new ProfileUpdateRequest("  닉네임 ", "홍길동", null, null, null).nickname());
        assertEquals("대학생", new ProfileUpdateRequest("닉네임", "홍길동", null, " 대학생 ", null).job());
        assertNull(new ProfileUpdateRequest("닉네임", "홍길동", null, "  ", null).job());
    }

    @Test
    void nameIsRequiredLikeSignup() {
        assertFalse(valid("닉네임", null));
        assertFalse(valid("닉네임", "   "));
        assertFalse(valid("닉네임", "홍"));
        assertTrue(valid("닉네임", "홍길"));
        assertFalse(valid("닉네임", "가".repeat(31)));
    }
}
