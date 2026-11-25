package com.vori.backend.attendance;

import com.vori.backend.notification.NotificationService;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetStatRewardService;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 월별 출석 조회 범위 — Between 은 양 끝을 포함하므로 끝값은 그 달 마지막 날이어야 한다. */
class AttendanceMonthRangeTest {

    private final AttendanceCheckinRepository checkins = mock(AttendanceCheckinRepository.class);
    private final AttendanceService service = new AttendanceService(checkins, mock(UserStatItemRepository.class),
            mock(UserRepository.class), mock(PetRepository.class), mock(PetGrowthLogRepository.class),
            mock(PetStatRewardService.class), mock(NotificationService.class), mock(ApplicationEventPublisher.class));

    @Test
    @DisplayName("9월 출석 조회에 10월 1일이 섞이지 않는다")
    void monthRangeEndsOnLastDay() {
        service.month(1L, YearMonth.of(2026, 9));
        verify(checkins).findByUserIdAndCheckedInDateBetweenOrderByCheckedInDate(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }
}
