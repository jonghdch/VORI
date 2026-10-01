package com.vori.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.inquiry.ReasonCategory;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.pet.PetStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 일일 리포트가 펫 코멘트에 넘기는 맥락(펫 이름·지출 이유) 검증.
 * 리포지토리는 목으로 두고, GeminiClient 가 받은 입력만 본다.
 */
class DailyReportServiceTest {

    private final DailyReportRepository dailyReportRepository = mock(DailyReportRepository.class);
    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final IncomeRepository incomeRepository = mock(IncomeRepository.class);
    private final PetRepository petRepository = mock(PetRepository.class);
    private final PetSpeciesRepository petSpeciesRepository = mock(PetSpeciesRepository.class);
    private final PetGrowthLogRepository petGrowthLogRepository = mock(PetGrowthLogRepository.class);
    private final AiInquiryRepository aiInquiryRepository = mock(AiInquiryRepository.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);

    private final DailyReportService service = new DailyReportService(
            dailyReportRepository, expenseRepository, incomeRepository, petRepository,
            petSpeciesRepository, petGrowthLogRepository, aiInquiryRepository, geminiClient,
            mock(TransactionTemplate.class), new ObjectMapper());

    private static final Long USER = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

    private static Expense expense(long id, int amount, int saved) {
        Expense e = mock(Expense.class);
        when(e.getId()).thenReturn(id);
        when(e.getAmount()).thenReturn(amount);
        when(e.getSavedAmount()).thenReturn(saved);
        return e;
    }

    private static AiInquiry inquiry(ReasonCategory reason) {
        AiInquiry i = mock(AiInquiry.class);
        when(i.getReasonCategory()).thenReturn(reason);
        return i;
    }

    private void givenPet(String name) {
        Pet pet = mock(Pet.class);
        when(pet.getId()).thenReturn(1L);
        when(pet.getSpeciesId()).thenReturn(2L);
        when(pet.getName()).thenReturn(name);
        when(pet.getStage()).thenReturn(PetStage.JUVENILE);
        PetSpecies species = mock(PetSpecies.class);
        when(species.getName()).thenReturn("강아지");
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER)).thenReturn(List.of(pet));
        when(petSpeciesRepository.findById(2L)).thenReturn(Optional.of(species));
    }

    private GeminiClient.DailyCommentInput sentInput() {
        ArgumentCaptor<GeminiClient.DailyCommentInput> in = ArgumentCaptor.forClass(GeminiClient.DailyCommentInput.class);
        verify(geminiClient).generateDailyComment(in.capture());
        return in.getValue();
    }

    @Test
    @DisplayName("그날 지출 중 답한 질문의 이유만 넘긴다 — 아직 답하지 않은 질문은 빠진다")
    void passesOnlyAnsweredReasons() {
        // 목은 미리 만든다 — when(...) 안에서 다른 목을 스텁하면 Mockito 가 스텁 중단으로 본다
        List<Expense> expenses = List.of(expense(10, 200_000, -160_000), expense(11, 4_500, 3_000));
        List<AiInquiry> inquiries = List.of(inquiry(ReasonCategory.CEREMONY), inquiry(null));
        when(expenseRepository.findByUserIdAndSpentAtBetween(eq(USER), any(), any())).thenReturn(expenses);
        when(aiInquiryRepository.findByExpenseIdIn(List.of(10L, 11L))).thenReturn(inquiries);
        givenPet("콩이");

        service.generateNow(USER, DAY);

        GeminiClient.DailyCommentInput in = sentInput();
        assertThat(in.reasons()).containsExactly(ReasonCategory.CEREMONY);
        assertThat(in.petName()).isEqualTo("콩이");
        assertThat(in.speciesName()).isEqualTo("강아지");
        assertThat(in.expenseTotal()).isEqualTo(204_500);
        assertThat(in.savedAmount()).isEqualTo(-157_000);
    }

    @Test
    @DisplayName("지출이 없는 날은 질문을 조회하지 않고 이유 없이 넘긴다")
    void noExpensesNoInquiryLookup() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(eq(USER), any(), any())).thenReturn(List.of());
        givenPet(null);

        service.generateNow(USER, DAY);

        GeminiClient.DailyCommentInput in = sentInput();
        assertThat(in.reasons()).isEmpty();
        assertThat(in.petName()).isNull();
        verify(aiInquiryRepository, never()).findByExpenseIdIn(anyList());
    }
}
