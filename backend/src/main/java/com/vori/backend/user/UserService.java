package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
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

    private void initializeStatStats(Long userId) {
        String sql = "INSERT INTO user_stat_stats " +
            "(user_id, stat_type, mean_ema, stddev_ema, sample_count) " +
            "VALUES (?, ?, 0, 0, 0)";
        for (String type : new String[]{"ENERGY", "CHARM", "IQ", "ENDURANCE"}) {
            jdbc.update(sql, userId, type);
        }
    }
}
