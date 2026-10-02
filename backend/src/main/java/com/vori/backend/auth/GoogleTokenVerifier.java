package com.vori.backend.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

/**
 * 구글 ID 토큰 검증 — 프론트의 Google Identity Services 가 준 credential(JWT)이
 * 정말 구글이 우리 앱에 발급한 것인지 확인하고, 그 안의 사용자 정보를 꺼낸다.
 *
 * 구글의 tokeninfo 엔드포인트에 토큰을 넘겨 검증한다. 서명·만료를 구글이 확인해 주므로
 * JWT 라이브러리나 구글 공개키 캐시가 필요 없다. 로그인마다 외부 호출이 한 번 늘지만
 * 이 규모에서는 문제되지 않는다. 우리가 추가로 보는 것은 세 가지다:
 *   aud            — 우리 클라이언트 ID 로 발급된 토큰인가 (다른 앱의 토큰 재사용 차단)
 *   email_verified — 구글이 이메일 소유를 확인했는가 (기존 계정 연결의 전제)
 *   sub            — 계정 고유 식별자 (users.google_sub)
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final RestTemplate restTemplate;
    private final String clientId;

    public GoogleTokenVerifier(RestTemplateBuilder builder,
                               @Value("${vori.google.client-id:}") String clientId) {
        this.restTemplate = builder
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(5))
            .build();
        this.clientId = clientId;
    }

    /** 검증된 토큰에서 꺼낸 값. name 은 구글 프로필에 없을 수 있다. */
    public record GoogleIdentity(String sub, String email, String name) {}

    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "구글 로그인이 설정되지 않았습니다 (GOOGLE_CLIENT_ID)");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "구글 인증 정보가 없습니다");
        }

        Map<?, ?> claims;
        try {
            claims = restTemplate.getForObject(TOKENINFO_URL + idToken, Map.class);
        } catch (RestClientException e) {
            // 서명 불일치·만료 토큰은 tokeninfo 가 4xx 로 답한다. 네트워크 장애도 여기로 온다.
            log.warn("[GoogleLogin] tokeninfo 실패 — {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 인증에 실패했습니다");
        }
        return validate(claims);
    }

    /** tokeninfo 응답(claims) 검사. 외부 호출과 분리해 두어 단위 테스트가 가능하다. */
    /** 구글 ID 토큰의 발급자 — 두 표기를 모두 쓴다(Google 문서). */
    private static final java.util.Set<String> ISSUERS =
        java.util.Set.of("accounts.google.com", "https://accounts.google.com");

    private static boolean notExpired(Object exp) {
        try {
            return exp != null && Long.parseLong(exp.toString()) > java.time.Instant.now().getEpochSecond();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    GoogleIdentity validate(Map<?, ?> claims) {
        if (claims == null || !clientId.equals(claims.get("aud"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "이 앱에 발급된 구글 인증 정보가 아닙니다");
        }
        // tokeninfo 가 서명·만료를 확인하지만, ID 토큰 규약(발급자·만료)은 여기서도 직접 본다
        if (!ISSUERS.contains(String.valueOf(claims.get("iss")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글이 발급한 인증 정보가 아닙니다");
        }
        if (!notExpired(claims.get("exp"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 구글 인증 정보입니다");
        }
        if (!"true".equals(String.valueOf(claims.get("email_verified")))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "구글에서 확인되지 않은 이메일입니다");
        }
        Object sub = claims.get("sub");
        Object email = claims.get("email");
        if (sub == null || email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 인증 정보가 불완전합니다");
        }
        Object name = claims.get("name");
        return new GoogleIdentity(sub.toString(), email.toString().toLowerCase(),
            name == null ? null : name.toString());
    }
}
