package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.user.dto.MeResponse;
import com.vori.backend.user.dto.ProfileUpdateRequest;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.title.TitleCheckEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.vori.backend.onboarding.BaselineSeeder baselineSeeder;

    @Transactional
    public User signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .nickname(req.nickname())
            .name(req.name())
            .role(Role.USER)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .marketingAgreedAt(Boolean.TRUE.equals(req.marketingAgreed()) ? now : null)
            .createdAt(now)
            .build();

        User saved = userRepository.save(user);
        initializeStatStats(saved.getId());
        grantStarterPet(saved.getId(), now);
        return saved;
    }

    /**
     * 구글 로그인으로 들어온 계정을 찾거나 만든다. 호출 전에 ID 토큰 검증이 끝나 있어야 한다.
     *
     * 1) google_sub 가 같은 계정이 있으면 그 계정 (이메일이 바뀌어도 같은 사람).
     * 2) 없고 이메일이 같은 기존 계정이 있으면 그 계정에 구글을 연결한다 — 구글이 소유를
     *    확인한 이메일(email_verified)만 여기까지 오므로 남의 계정에 붙을 수 없다.
     * 3) 둘 다 없으면 새로 만든다. 비밀번호는 없고(password_hash NULL), 약관은 구글 버튼
     *    아래 안내 문구로 동의한 것으로 본다. 일반 가입과 같은 초기화(스탯·시작 펫)를 거친다.
     */
    @Transactional
    public User findOrCreateGoogleUser(String googleSub, String email, String displayName) {
        Optional<User> bySub = userRepository.findByGoogleSub(googleSub);
        if (bySub.isPresent()) return bySub.get();

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            byEmail.get().linkGoogle(googleSub);
            return byEmail.get();
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .email(email)
            .googleSub(googleSub)
            .nickname(nicknameFrom(displayName, email))
            .name(displayName == null || displayName.isBlank() ? null : truncate(displayName, 30))
            .role(Role.USER)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .createdAt(now)
            .build();

        User saved = userRepository.save(user);
        initializeStatStats(saved.getId());
        grantStarterPet(saved.getId(), now);
        return saved;
    }

    /** 구글 표시 이름이 없으면 이메일 앞부분을 닉네임으로. nickname 은 NOT NULL·30자. */
    private static String nicknameFrom(String displayName, String email) {
        String base = displayName != null && !displayName.isBlank()
            ? displayName
            : email.substring(0, email.indexOf('@'));
        return truncate(base.trim(), 30);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 시작 펫 지급 — 가입 직후 키우기 화면이 비어 있지 않도록.
     * egg_id 는 NULL (가챠로 얻은 게 아님).
     *
     * 시드가 없어도 회원가입 자체는 성공해야 하므로 예외를 던지지 않고 경고만 남긴다.
     * 펫이 없으면 알을 사서 얻으면 되고, 계정이 안 만들어지는 쪽이 훨씬 나쁘다.
     */
    private void grantStarterPet(Long userId, LocalDateTime now) {
        List<PetSpecies> starters = petSpeciesRepository.findByIsStarterTrue();
        if (starters.isEmpty()) {
            log.warn("시작 펫 종족이 없어 지급을 건너뜀 — userId={}. "
                + "pet_species.is_starter 시드를 확인하세요(V7 마이그레이션).", userId);
            return;
        }

        PetSpecies species = starters.get(0);
        petRepository.save(Pet.builder()
            .userId(userId)
            .speciesId(species.getId())
            .hatchedAt(now)
            .createdAt(now)
            .build());
    }

    /**
     * 본인 정보 — 매번 DB 에서 읽는다. 세션(UserPrincipal)에는 신원만 있으므로
     * 닉네임·잔액처럼 바뀌는 값은 여기서만 나간다. /api/auth/me·/api/users/me·로그인 응답이 같이 쓴다.
     */
    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return userRepository.findById(userId)
            .map(MeResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }

    /**
     * 로그인 성공 시 호출 — 누적 로그인 횟수를 올리고 칭호 조건을 다시 본다.
     * AuthController.login() 이 인증 성공 직후 호출한다. "최초 1회 로그인" 같은
     * 조건은 커밋 이후 이벤트로 평가돼야 하므로 지출 등록 등과 같은 패턴을 따른다.
     */

    @Transactional
    public void recordLogin(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.incrementLoginCount();
        eventPublisher.publishEvent(new TitleCheckEvent(userId, "LOGIN"));
    }

    @Transactional
    public User updateProfile(Long userId, ProfileUpdateRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        // 공백 정리는 ProfileUpdateRequest 가 검사 전에 끝냈다.
        user.updateProfile(req.nickname(), req.name(), req.age(), req.job(), req.monthlyIncome());
        // 월 수입이 바뀌면 실제 지출이 없는 타입의 초기 기준선을 다시 잡는다(온보딩 씨딩과 같은 규칙).
        baselineSeeder.reseedFromIncome(userId, req.monthlyIncome());
        return user;
    }

    private void initializeStatStats(Long userId) {
        String sql = "INSERT INTO user_stat_stats " +
            "(user_id, stat_type, mean_ema, stddev_ema, sample_count) " +
            "VALUES (?, ?, 0, 0, 0)";
        for (String type : new String[]{"ENERGY", "CHARM", "IQ", "ENDURANCE"}) {
            jdbc.update(sql, userId, type);
        }
    }
}
