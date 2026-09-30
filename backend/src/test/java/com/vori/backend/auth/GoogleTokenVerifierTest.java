package com.vori.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** tokeninfo 응답 검사 규칙 — 네트워크 없이 claims 만 넣어 확인한다. */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";

    private final GoogleTokenVerifier verifier =
        new GoogleTokenVerifier(new RestTemplateBuilder(), CLIENT_ID);

    private static Map<String, Object> validClaims() {
        Map<String, Object> m = new HashMap<>();
        m.put("aud", CLIENT_ID);
        m.put("sub", "1078123456789");
        m.put("email", "Someone@Gmail.com");
        m.put("email_verified", "true");
        m.put("name", "홍길동");
        m.put("iss", "https://accounts.google.com");
        m.put("exp", String.valueOf(java.time.Instant.now().getEpochSecond() + 3600));
        return m;
    }

    @Test
    void 정상_토큰이면_sub_email_name_을_돌려준다_이메일은_소문자() {
        GoogleTokenVerifier.GoogleIdentity id = verifier.validate(validClaims());
        assertEquals("1078123456789", id.sub());
        assertEquals("someone@gmail.com", id.email());
        assertEquals("홍길동", id.name());
    }

    @Test
    void name_이_없어도_통과한다() {
        Map<String, Object> c = validClaims();
        c.remove("name");
        assertNull(verifier.validate(c).name());
    }

    @Test
    void 다른_앱에_발급된_토큰은_401() {
        Map<String, Object> c = validClaims();
        c.put("aud", "other-app.apps.googleusercontent.com");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> verifier.validate(c));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void 이메일_미확인_계정은_403() {
        Map<String, Object> c = validClaims();
        c.put("email_verified", "false");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> verifier.validate(c));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void 클라이언트_ID_미설정이면_503() {
        GoogleTokenVerifier unset = new GoogleTokenVerifier(new RestTemplateBuilder(), "");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> unset.verify("x.y.z"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void 구글이_발급하지_않은_토큰은_401() {
        Map<String, Object> c = validClaims();
        c.put("iss", "https://evil.example.com");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> verifier.validate(c));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void 만료된_토큰은_401() {
        Map<String, Object> c = validClaims();
        c.put("exp", String.valueOf(java.time.Instant.now().getEpochSecond() - 10));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> verifier.validate(c));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }
}
