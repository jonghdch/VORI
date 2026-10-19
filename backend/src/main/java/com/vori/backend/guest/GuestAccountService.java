package com.vori.backend.guest;

import com.vori.backend.attendance.AttendanceService;
import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.ExpenseService;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.onboarding.MealCostBand;
import com.vori.backend.onboarding.MonthlyBudgetBand;
import com.vori.backend.onboarding.MonthlyGoal;
import com.vori.backend.onboarding.OnboardingService;
import com.vori.backend.onboarding.PrimarySpendArea;
import com.vori.backend.onboarding.SpendingHabit;
import com.vori.backend.onboarding.dto.SpendingProfileRequest;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import com.vori.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 「체험하기」 — 경진대회(11/11) 관람객이 부스 QR 로 들어와 가입 없이 바로 써 보는 임시 계정.
 *
 * <p>한 번에 만드는 것 (짧은 체험에도 판정·업적·상점이 보이게 — 10/17 팀 결정):
 * <ul>
 *   <li>계정: 이메일 guest-…@guest.vori.local, 비밀번호 없음. 대회가 끝나면 이 도메인으로 한꺼번에 지운다.</li>
 *   <li>소비 설문: 고정 답 — 기준선이 심어져 첫 지출부터 판정된다(BaselineSeeder).</li>
 *   <li>지난 이틀 지출 6건 — 평소 금액이라 빨강이 아니다. 빨강이면 AI 질문을 만들어 Gemini 를 쓴다.</li>
 *   <li>코인 +1,500 — 가입 축하 1,000 과 합쳐 가구 2~3개를 살 수 있게. 지출 세팅이 그날 기록 보상 100 도 받아
 *       시작 코인은 2,600 이 된다(대신 게스트가 직접 쓴 첫 지출에는 기록 보상이 없다).</li>
 *   <li>오늘 출석 — 출석 팝업은 미리 처리하고, 펫 이름 짓기는 체험의 일부로 남긴다.</li>
 * </ul>
 *
 * <p>하루 생성 상한(vori.guest.daily-limit) — 대회장 관람객은 같은 와이파이(같은 공인 IP)를 써서 IP 별 제한은
 * 모두를 함께 막는다. 전체 상한으로 남용·Gemini 비용 폭주를 막는다. 0 이면 체험하기를 끈다.
 */
@Service
@RequiredArgsConstructor
public class GuestAccountService {

    public static final String EMAIL_DOMAIN = "@guest.vori.local";
    static final int BONUS_COINS = 1_500;
    static final String GUEST_NAME = "체험 계정";

    /** 지난 이틀 기록 — 며칠 전·몇 시·카테고리(소분류 이름)·금액·내역. */
    record SeedExpense(int daysAgo, int hour, String category, int amount, String item) {}

    static final List<SeedExpense> SEED_EXPENSES = List.of(
            new SeedExpense(2, 12, "외식", 6_000, "학식 정식"),
            new SeedExpense(2, 15, "카페", 4_500, "아메리카노"),
            new SeedExpense(2, 19, "대중교통", 1_450, "지하철"),
            new SeedExpense(1, 8, "편의점", 1_800, "삼각김밥"),
            new SeedExpense(1, 13, "외식", 7_000, "떡볶이"),
            new SeedExpense(1, 20, "도서", 12_000, "중고 전공책"));

    /** 설문 고정 답 — 한 끼 7~10천 원(식비 기준선), 월수입 80만 원(그 밖의 기준선). */
    static final SpendingProfileRequest PROFILE = new SpendingProfileRequest(
            800_000, MonthlyBudgetBand.UNKNOWN, MealCostBand.MEAL_7_10,
            PrimarySpendArea.FOOD_CAFE, SpendingHabit.UNKNOWN, MonthlyGoal.GROW_PET);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final UserRepository userRepository;
    private final UserService userService;
    private final OnboardingService onboardingService;
    private final ExpenseService expenseService;
    private final AttendanceService attendanceService;
    private final CategoryRepository categoryRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${vori.guest.daily-limit:300}")
    private int dailyLimit;

    public static boolean isGuestEmail(String email) {
        return email != null && email.endsWith(EMAIL_DOMAIN);
    }

    /** 체험 계정을 만들고 세팅한다. 하나라도 실패하면 전부 되돌린다(반쯤 만든 계정이 남지 않게). */
    @Transactional
    public User create() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        if (dailyLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지금은 체험하기를 쓸 수 없어요. 회원가입으로 시작해 주세요.");
        }
        long madeToday = userRepository.countByEmailEndingWithAndCreatedAtGreaterThanEqual(
                EMAIL_DOMAIN, today.atStartOfDay());
        if (madeToday >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "오늘 준비한 체험 계정을 다 썼어요. 회원가입으로 시작해 주세요.");
        }

        User user = userService.createGuest(
                "guest-" + now.format(DAY) + "-" + hex(8) + EMAIL_DOMAIN,
                "체험" + String.format("%04d", random.nextInt(10_000)),
                GUEST_NAME);
        Long userId = user.getId();

        onboardingService.saveProfile(userId, PROFILE);
        onboardingService.complete(userId);

        for (SeedExpense e : SEED_EXPENSES) {
            Long categoryId = categoryRepository.findFirstByName(e.category())
                    .map(Category::getId)
                    .orElseThrow(() -> new IllegalStateException("체험 계정 세팅용 카테고리가 없다: " + e.category()));
            expenseService.createExpense(userId, new ExpenseCreateRequest(
                    categoryId, e.amount(), e.item(),
                    today.minusDays(e.daysAgo()).atTime(e.hour(), 0),
                    true, null, null, false));
        }

        // 같은 트랜잭션의 영속 객체 — 지출 저장이 바꾼 값(기록 보상 등)을 그대로 이어받아 더한다
        User saved = userRepository.findById(userId).orElseThrow();
        saved.addGameMoney(BONUS_COINS);

        attendanceService.checkIn(userId);
        return saved;
    }

    private String hex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).substring(0, length);
    }
}
