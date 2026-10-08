package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.user.dto.MeResponse;
import com.vori.backend.user.dto.ProfileUpdateRequest;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.budget.SpendingPlanService;
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
    private final SpendingPlanService spendingPlanService;

    @Transactional
    public User signup(SignupRequest req) {
        Optional<User> existing = userRepository.findByEmail(req.email());
        if (existing.isPresent()) {
            // 탈퇴 대기 계정이면 새로 만들 게 아니라 로그인으로 복구하면 된다고 알려 준다
            throw new ResponseStatusException(HttpStatus.CONFLICT, existing.get().isPendingDeletion()
                ? "탈퇴 대기 중인 계정이에요. 로그인하면 복구돼요."
                : "이미 사용 중인 이메일입니다");
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

        User saved = userRepository.saveAndFlush(user);
        initializeStatStats(saved.getId());
        grantStarterPet(saved.getId(), now);
        return saved;
    }

    /**
     * 구글 로그인으로 들어온 계정을 찾거나 만든다. 호출 전에 ID 토큰 검증이 끝나 있어야 한다.
     *
     * 1) google_sub 가 같은 계정이 있으면 그 계정 (이메일이 바뀌어도 같은 사람).
     * 2) 없고 이메일이 같은 기존 계정이 있으면 409 — 자동으로 연결하지 않는다. 이메일 가입은 소유를
     *    확인하지 않으므로, 남이 먼저 그 이메일로 가입해 둔 계정에 진짜 주인의 구글을 붙이면 가입한
     *    사람이 자기 비밀번호로 주인의 기록을 볼 수 있게 된다(탈취). 이미 다른 구글이 연결된 계정을
     *    덮어쓰는 일도 같이 막힌다.
     * 3) 둘 다 없으면 새로 만든다. 비밀번호는 없고(password_hash NULL), 약관은 구글 버튼
     *    아래 안내 문구로 동의한 것으로 본다. 일반 가입과 같은 초기화(스탯·시작 펫)를 거친다.
     *    닉네임·이름은 가입 규칙(닉네임 2~12자, 이름 2~30자)에 맞춘다 — 어긋나면 설문 뒤 프로필
     *    저장이 400 으로 막힌다.
     *
     * 첫 로그인이 동시에 두 번 오면 한쪽 저장이 유니크 제약에 걸린다. 여기서는 saveAndFlush 로 바로
     * 드러내고, 호출부(AuthController)가 새 트랜잭션으로 한 번 더 찾는다.
     */
    @Transactional
    public User findOrCreateGoogleUser(String googleSub, String email, String displayName) {
        Optional<User> bySub = userRepository.findByGoogleSub(googleSub);
        if (bySub.isPresent()) return bySub.get();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "이미 이메일로 가입된 계정이에요. 이메일로 로그인해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .email(email)
            .googleSub(googleSub)
            .nickname(nicknameFrom(displayName, email))
            .name(nameFrom(displayName))
            .role(Role.USER)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .createdAt(now)
            .build();

        User saved = userRepository.saveAndFlush(user);
        initializeStatStats(saved.getId());
        grantStarterPet(saved.getId(), now);
        return saved;
    }

    /** 구글 표시 이름이 없으면 이메일 앞부분을 닉네임으로. nickname 은 NOT NULL·30자. */
    // 가입 규칙(SignupRequest)과 같은 길이 — 닉네임 2~12자, 이름 2~30자
    private static final int NICKNAME_MIN = 2, NICKNAME_MAX = 12, NAME_MIN = 2, NAME_MAX = 30;

    /** 구글 이름(없으면 이메일 앞부분)으로 닉네임을 만든다. 12자로 자르고, 2자 미만이면 뒤를 채운다. */
    private static String nicknameFrom(String displayName, String email) {
        String base = displayName != null && !displayName.isBlank()
            ? displayName
            : email.substring(0, email.indexOf('@'));
        String nickname = truncate(base.trim(), NICKNAME_MAX);
        return nickname.length() >= NICKNAME_MIN ? nickname : nickname + "님";
    }

    /** 구글 이름을 이름 칸에. 2자 미만이면 비워 두고 프로필 설정에서 채우게 한다. */
    private static String nameFrom(String displayName) {
        if (displayName == null) return null;
        String name = truncate(displayName.trim(), NAME_MAX);
        return name.length() >= NAME_MIN ? name : null;
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
     *
     * @return 탈퇴 대기 계정이라 이번 로그인으로 복구됐으면 true
     */

    @Transactional
    public boolean recordLogin(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.incrementLoginCount();
        // 탈퇴 유예 기간 안에 로그인하면 탈퇴를 취소한다 (AccountDeletionService)
        boolean restored = user.cancelDeletion();
        if (restored) log.info("탈퇴 취소(로그인 복구) — userId={}", userId);
        eventPublisher.publishEvent(new TitleCheckEvent(userId, "LOGIN"));
        return restored;
    }

    @Transactional
    public User updateProfile(Long userId, ProfileUpdateRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        Integer previousIncome = user.getMonthlyIncome();
        // 공백 정리는 ProfileUpdateRequest 가 검사 전에 끝냈다.
        user.updateProfile(req.nickname(), req.name(), req.age(), req.job(), req.monthlyIncome());
        // 월 수입이 바뀌면 실제 지출이 없는 타입의 초기 기준선을 다시 잡는다(온보딩 씨딩과 같은 규칙).
        baselineSeeder.reseedFromIncome(userId, req.monthlyIncome());
        if (!java.util.Objects.equals(previousIncome, req.monthlyIncome())) {
            spendingPlanService.rebuildCurrentPlan(userId);
        }
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
