package com.vori.backend.judgment;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DailyJudgmentServiceTest {
    private final DailyJudgmentRepository judgments = mock(DailyJudgmentRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final PetGrowthLogRepository growthLogs = mock(PetGrowthLogRepository.class);
    private final UserFurnitureRepository furniture = mock(UserFurnitureRepository.class);
    private final DailyJudgmentService service = new DailyJudgmentService(judgments, expenses, users, pets, growthLogs, furniture);

    @Test
    void greenDailyJudgmentGivesFixedCoinsAndAllFourStats() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = Pet.builder().id(9L).userId(1L).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.empty());
        when(expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(Expense.builder().signalFinal(Signal.GREEN).build()));
        when(pets.findByUserIdAndReleasedAtIsNull(1L)).thenReturn(List.of(pet));
        when(furniture.findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(1L)).thenReturn(List.of());
        when(judgments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertEquals(500, result.coinReward());
        assertEquals(11, result.statRewardPerType());
        assertEquals(500, user.getGameMoney());
        assertEquals(44, pet.statTotal());
        verify(growthLogs, times(4)).save(any());
    }

    @Test
    void existingJudgmentDoesNotGrantRewardsAgain() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        DailyJudgment existing = DailyJudgment.builder().userId(1L).judgmentDate(date)
                .signal(Signal.GREEN).expenseCount(0).coinReward(500).statRewardPerType(11)
                .judgedAt(java.time.LocalDateTime.now()).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.of(existing));

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertTrue(result.alreadyJudged());
        assertEquals(0, user.getGameMoney());
        verifyNoInteractions(pets, growthLogs, furniture);
        verify(judgments, never()).save(any());
    }

    @Test
    void regularUserCannotJudgeAnotherDate() {
        User user = User.builder().id(2L).role(Role.USER).build();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.judgeDate(user.getId(), user.getRole(), LocalDate.now().minusDays(1)));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(expenses, judgments, users, pets, growthLogs, furniture);
    }
}
