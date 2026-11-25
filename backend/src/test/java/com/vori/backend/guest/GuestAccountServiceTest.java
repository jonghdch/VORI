package com.vori.backend.guest;

import com.vori.backend.attendance.AttendanceService;
import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.ExpenseService;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.onboarding.OnboardingService;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import com.vori.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 「체험하기」 임시 계정 — 하루 상한과 한 번에 만드는 세팅. DB 없이 돈다. */
class GuestAccountServiceTest {

    private static final long USER_ID = 7L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final OnboardingService onboardingService = mock(OnboardingService.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private GuestAccountService service;
    private User guest;

    @BeforeEach
    void setUp() {
        service = new GuestAccountService(userRepository, userService, onboardingService,
                expenseService, attendanceService, categoryRepository);
        ReflectionTestUtils.setField(service, "dailyLimit", 300);
        guest = User.builder().id(USER_ID).email("guest-x@guest.vori.local").gameMoney(1_000).build();
        when(userService.createGuest(anyString(), anyString(), anyString())).thenReturn(guest);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(categoryRepository.findFirstByName(anyString()))
                .thenAnswer(inv -> Optional.of(Category.builder().id(100L).name(inv.getArgument(0)).build()));
    }

    @Test
    void 오늘_상한에_닿으면_429_이고_계정을_만들지_않는다() {
        when(userRepository.countByEmailEndingWithAndCreatedAtGreaterThanEqual(eq(GuestAccountService.EMAIL_DOMAIN), any()))
                .thenReturn(300L);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, service::create);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        verify(userService, never()).createGuest(anyString(), anyString(), anyString());
    }

    @Test
    void 상한이_0이면_체험하기가_꺼진다() {
        ReflectionTestUtils.setField(service, "dailyLimit", 0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, service::create);

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(userService);
    }

    @Test
    void 체험_도메인_이메일로_만들고_설문_지출_코인_출석까지_한_번에_세팅한다() {
        User created = service.create();

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(userService).createGuest(email.capture(), nickname.capture(), eq("체험 계정"));
        assertTrue(email.getValue().matches("guest-\\d{6}-[0-9a-f]{8}@guest\\.vori\\.local"), email.getValue());
        assertTrue(GuestAccountService.isGuestEmail(email.getValue()));
        // 가입 규칙과 같은 닉네임 길이(2~12자)
        assertTrue(nickname.getValue().matches("체험\\d{4}"), nickname.getValue());

        verify(onboardingService).saveProfile(USER_ID, GuestAccountService.PROFILE);
        verify(onboardingService).complete(USER_ID);

        ArgumentCaptor<ExpenseCreateRequest> expenses = ArgumentCaptor.forClass(ExpenseCreateRequest.class);
        verify(expenseService, times(GuestAccountService.SEED_EXPENSES.size()))
                .createExpense(eq(USER_ID), expenses.capture());
        List<ExpenseCreateRequest> seeded = expenses.getAllValues();
        // 오늘 판정에는 관람객이 직접 적은 지출만 들어가게 — 미리 넣는 지출은 모두 어제와 그 이전
        assertTrue(seeded.stream().allMatch(r -> r.spentAt().toLocalDate().isBefore(LocalDate.now())));

        assertEquals(1_000 + GuestAccountService.BONUS_COINS, created.getGameMoney());
        verify(attendanceService).checkIn(USER_ID);
    }

    @Test
    void 일반_이메일은_체험_계정이_아니다() {
        assertTrue(!GuestAccountService.isGuestEmail("someone@gmail.com"));
        assertTrue(!GuestAccountService.isGuestEmail(null));
    }
}
