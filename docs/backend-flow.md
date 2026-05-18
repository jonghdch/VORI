# 백엔드 요청 흐름

> 클라이언트 요청이 들어와서 응답이 나갈 때까지 거치는 레이어와 각 레이어의 책임. 신규 기능 추가 시 이 패턴을 따른다.

## 전체 그림

```
[Client (React)]
       │  HTTP + JSON + JSESSIONID 쿠키
       ▼
┌─────────────────────────────────────────┐
│  Spring Security FilterChain             │
│  - CORS / 인증·권한 체크 / 세션 복원      │
└─────────────────────────────────────────┘
       │  통과
       ▼
┌─────────────────────────────────────────┐
│  Controller (@RestController)            │
│  - HTTP 매핑, 입력 검증(@Valid),          │
│    DTO 변환, HTTP status 결정              │
└─────────────────────────────────────────┘
       │  Request DTO
       ▼
┌─────────────────────────────────────────┐
│  Service (@Service @Transactional)       │
│  - 비즈 로직, 트랜잭션 경계,              │
│    여러 Repository 조합                   │
└─────────────────────────────────────────┘
       │  Entity
       ▼
┌─────────────────────────────────────────┐
│  Repository (extends JpaRepository)      │
│  - DB CRUD. JPA 가 SQL 자동 생성          │
└─────────────────────────────────────────┘
       │  SQL
       ▼
┌─────────────────────────────────────────┐
│  MySQL                                   │
└─────────────────────────────────────────┘
```

응답은 역순:

```
DB → Entity → Service 가공 → Response DTO → Controller → JSON 응답
```

## 레이어별 책임·금기

| 레이어 | 무엇 | 무엇 아님 |
|---|---|---|
| **Controller** | HTTP 매핑·검증(`@Valid`)·DTO 변환·HTTP status 결정 | 비즈 로직 X · DB 접근 X · Entity 직접 반환 X |
| **Service** | 비즈 로직 · 트랜잭션 경계(`@Transactional`) · Repository 조합 · 도메인 규칙 검증 | HTTP 의존 X (`HttpServletRequest` 받지 말 것) · DTO 변환은 정적 팩토리 정도만 |
| **Repository** | DB CRUD · 메서드 이름으로 쿼리 자동 생성 · 복잡 쿼리는 `@Query` JPQL/native | 비즈 로직 X · 트랜잭션 시작 X (Service 가 경계) |
| **Entity** | DB 테이블 ↔ Java 객체 매핑 · 도메인 상태 보유 | 외부 노출 X (Response 로 직접 쓰지 말 것) · 비즈 로직 메서드 가능하지만 최소화 |
| **DTO** | 외부와 주고받는 데이터 컨테이너 | 비즈 로직 X (단순 데이터) · Entity 직접 참조 X (의존 역방향 금지) |

## 데이터 형태가 바뀌는 지점

```
JSON ↔ Request/Response DTO   ←  Controller 가 변환 (Jackson 자동)
DTO  → Entity                  ←  Service 안에서 변환 (User.builder())
Entity ↔ DB row                ←  JPA 가 자동 변환
```

**핵심 규칙**: Entity 를 Controller 응답에 직접 쓰지 말 것. 항상 Response DTO 로 감싸야 한다.

이유:
- Entity 변경이 API 응답 형태에 그대로 새어 나감 → 프론트 깨짐
- `password_hash` 같이 외부 노출 금지 컬럼이 같이 나갈 수 있음
- JPA Lazy 로딩이 직렬화 시점에 트리거돼서 `LazyInitializationException`

## 트랜잭션 경계

`@Transactional` 은 **Service 메서드**에 붙임. Controller·Repository 엔 X.

```java
@Service
@RequiredArgsConstructor
public class UserService {
    @Transactional
    public User signup(SignupRequest req) {
        // 이 메서드 안 모든 DB 작업이 하나의 트랜잭션
        userRepository.save(user);
        initializeStatStats(saved.getId());  // 둘 다 성공해야 commit
                                             // 하나라도 throw 면 rollback
    }
}
```

## 실제 예시 — 회원가입 (POST /api/auth/signup)

```
1) [Browser]
   POST /api/auth/signup
   Headers: Content-Type: application/json, Origin: http://localhost:3000
   Body: {
     "email":"a@b.com", "password":"vori!2026",
     "nickname":"닉", "name":"홍길동",
     "termsAgreed":true, "privacyAgreed":true, "marketingAgreed":false
   }
       │
       ▼
2) [Spring Security FilterChain]
   - CORS preflight 통과 (Origin 매칭, credentials 허용)
   - SecurityConfig.filterChain() 의 permitAll 리스트에 /api/auth/signup 있음 → 통과
       │
       ▼
3) [AuthController.signup(req)]
   @PostMapping("/signup") @ResponseStatus(CREATED)
   - @Valid 가 SignupRequest 의 @NotBlank·@Email·@Pattern·@AssertTrue 검증
   - 실패 → MethodArgumentNotValidException → 400 자동 반환
   - 통과 → userService.signup(req) 호출
       │  SignupRequest
       ▼
4) [UserService.signup(req)]  @Transactional
   - userRepository.existsByEmail(req.email()) → 중복이면 ResponseStatusException(CONFLICT)
   - passwordEncoder.encode(req.password()) → bcrypt 해시
   - User.builder()...build() → Entity 생성
   - userRepository.save(user) → users INSERT
   - initializeStatStats(saved.getId()) → user_stat_stats 4행 INSERT (JdbcTemplate)
       │  User entity
       ▼
5) [AuthController] AuthResponse.from(user) → JSON 직렬화
       │  AuthResponse DTO
       ▼
6) HTTP 201 Created
   Body: {"id":7,"email":"a@b.com","nickname":"닉","role":"USER"}
```

## 실제 예시 — 로그인 (POST /api/auth/login)

```
1) POST /api/auth/login  Body: { "email":"...", "password":"..." }
       │
       ▼
2) FilterChain 통과 (permitAll)
       │
       ▼
3) [AuthController.login(req, request, response)]
   - authenticationManager.authenticate(UsernamePasswordAuthenticationToken)
       │
       ▼
4) [DaoAuthenticationProvider]
   - CustomUserDetailsService.loadUserByUsername(email)
     → userRepository.findByEmail() → UserPrincipal 반환
   - passwordEncoder.matches(평문, user.password_hash) → true/false
   - 실패 → BadCredentialsException → Controller 가 401 변환
       │  Authentication 객체
       ▼
5) [AuthController]
   - SecurityContextHolder 에 Authentication 저장
   - securityContextRepository.saveContext() → HttpSession 에 영속화
       │
       ▼
6) HTTP 200 OK
   Headers: Set-Cookie: JSESSIONID=...; HttpOnly
   Body: AuthResponse JSON
```

## 폴더 위치

기능 단위 패키징이라 한 도메인 안에 다 모임:

```
user/
├── User.java                  ← Entity
├── UserRepository.java        ← Repository (DAO)
├── UserService.java           ← Service
├── Role.java                  ← Enum
└── (UserController 없음. auth/AuthController 가 user 도메인도 다룸)

auth/
├── AuthController.java        ← Controller
├── UserPrincipal.java         ← Spring Security UserDetails 구현
├── CustomUserDetailsService.java
└── dto/
    ├── SignupRequest.java     ← Request DTO
    ├── LoginRequest.java
    └── AuthResponse.java      ← Response DTO
```

## 신규 기능 추가 체크리스트

새 도메인 기능을 만든다면:

1. **Entity·Repository 있나?** — 18개 테이블 다 만들어져 있음. 새 테이블이면 V2 마이그레이션 추가 후 entity 작성
2. **DTO 정의** — `<도메인>/dto/<기능>Request.java`, `<기능>Response.java`. `@NotBlank` 등 검증 어노테이션 적극 사용
3. **Service 작성** — `@Service @Transactional`. 비즈 룰은 [`domain.md`](domain.md) 참조
4. **Controller 작성** — `@RestController @RequestMapping("/api/<도메인>")`. 메서드는 `@Valid @RequestBody` 로 검증 받기
5. **Security 설정 확인** — 인증 필요 엔드포인트면 자동으로 보호됨. 공개 엔드포인트면 `SecurityConfig` 의 `permitAll` 에 추가
6. **테스트** — `curl` 로 happy path + 실패 케이스 (검증·중복·권한) 최소 3건

## 자주 막히는 함정

- **Service 가 `HttpServletRequest` 받지 말 것** — Controller 가 필요한 값만 추출해서 Service 에 넘기기. Service 가 웹 의존성 가지면 테스트 어려움
- **Repository 에 비즈 로직 X** — `userRepository.signupAndInitStats(...)` 같은 메서드 X. Service 가 조합해야 함
- **Entity setter 남발 X** — 가능하면 메서드(`user.changePassword(newHash)`)로 의도 표현. 우리 Entity 들은 `@Getter` 만 있고 setter 없음
- **Entity 를 Response 로 직접 반환 X** — 항상 DTO 로 감싸기. `AuthResponse.from(user)` 같은 정적 팩토리 패턴
- **`@Transactional` 깜빡 X** — 여러 Repository 호출이 있으면 반드시 붙이기. 안 그러면 부분 실패 시 데이터 불일치
- **예외 던질 때** — 비즈 룰 위반은 `ResponseStatusException(HttpStatus.XXX, "메시지")` 가 빠름. 복잡해지면 도메인 예외 클래스 만들어서 `@RestControllerAdvice` 로 처리

## 관련 문서

- [`README.md`](../README.md) — 도메인 패키지 목록·셋업
- [`architecture.md`](architecture.md) — 시스템 경계·외부 의존
- [`domain.md`](domain.md) — 비즈 룰·계산식 (Service 짤 때 필수 참조)
- [`db-spec.md`](db-spec.md) — 테이블 스키마
- [`../DATABASE.md`](../DATABASE.md) — DB 셋업·Flyway·트러블슈팅
