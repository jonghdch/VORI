package com.vori.backend.pet;

import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.gemini.GeminiClient.ChatTurn;
import com.vori.backend.pet.dto.PetChatRequest;
import com.vori.backend.pet.dto.PetChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 펫 대화의 하루 횟수 제한과 대화 턴 구성 검증. Gemini 는 목으로 고정한다.
 */
class PetChatServiceTest {

    private static final long USER_ID = 7L;

    private final PetRepository petRepository = mock(PetRepository.class);
    private final PetSpeciesRepository speciesRepository = mock(PetSpeciesRepository.class);
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 2));
    private final PetChatService service =
            new PetChatService(petRepository, speciesRepository, gemini, mock(PetLedgerSummary.class), 2, today::get);

    private void givenPet() {
        Pet pet = Pet.builder().id(3L).userId(USER_ID).speciesId(1L).name("콩이")
                .statIq(20).createdAt(LocalDateTime.now()).build();
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of(pet));
        when(speciesRepository.findById(1L)).thenReturn(Optional.of(
                PetSpecies.builder().id(1L).name("다람쥐").tier(PetTier.B).appearanceKey("squirrel").build()));
    }

    private static PetChatRequest say(String message) {
        return new PetChatRequest(message, List.of());
    }

    @Test
    @DisplayName("하루 횟수를 다 쓰면 429, 날짜가 바뀌면 다시 대화할 수 있다")
    void dailyLimitResetsNextDay() {
        givenPet();
        when(gemini.chat(anyString(), anyList())).thenReturn("안녕하세요!");

        assertThat(service.chat(USER_ID, say("안녕")).remaining()).isEqualTo(1);
        assertThat(service.chat(USER_ID, say("뭐 해?")).remaining()).isZero();
        assertThatThrownBy(() -> service.chat(USER_ID, say("또 안녕")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        today.set(today.get().plusDays(1));
        assertThat(service.status(USER_ID).remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 503 을 내고 횟수는 차감하지 않는다")
    void failedCallDoesNotCount() {
        givenPet();
        when(gemini.chat(anyString(), anyList())).thenThrow(new RuntimeException("AI 서비스 호출에 실패했습니다."));

        assertThatThrownBy(() -> service.chat(USER_ID, say("안녕")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(service.status(USER_ID).remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("응답에 지금 성격 이름이 실린다 — 지능 우세 펫은 계산쟁이")
    void responseCarriesPersonality() {
        givenPet();
        when(gemini.chat(anyString(), anyList())).thenReturn("  도토리 세는 중이에요!  ");

        PetChatResponse res = service.chat(USER_ID, say("뭐 해?"));

        assertThat(res.reply()).isEqualTo("도토리 세는 중이에요!");
        assertThat(res.personality()).isEqualTo("꼼꼼한 계산쟁이");
    }

    @Test
    @DisplayName("키우는 펫이 없으면 400")
    void noPetIsBadRequest() {
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.chat(USER_ID, say("안녕")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("대화 턴은 사용자 말로 시작하고 이번 말이 마지막에 붙는다")
    void turnsStartWithUserAndEndWithMessage() {
        PetChatRequest req = new PetChatRequest("오늘 뭐 먹었어?", List.of(
                new PetChatRequest.Turn("pet", "안녕하세요!"),
                new PetChatRequest.Turn("user", "안녕"),
                new PetChatRequest.Turn("pet", "반가워요!")));

        List<ChatTurn> turns = PetChatService.turns(req);

        assertThat(turns).extracting(ChatTurn::fromUser).containsExactly(true, false, true);
        assertThat(turns.get(turns.size() - 1).text()).isEqualTo("오늘 뭐 먹었어?");
    }

    @Test
    @DisplayName("시스템 프롬프트에 이름·종족·성격·가계부 요약·대화 규칙이 들어간다")
    void systemPromptHasPersonaAndRules() {
        Pet pet = Pet.builder().userId(USER_ID).speciesId(1L).name("콩이")
                .statIq(20).createdAt(LocalDateTime.now()).build();
        PetSpecies species = PetSpecies.builder().name("다람쥐").tier(PetTier.B).appearanceKey("squirrel").build();

        String prompt = PetChatService.systemPrompt(pet, species, "- 오늘 지출: 2건, 합계 12,000원");

        assertThat(prompt).contains("다람쥐 '콩이'", "도토리", "꼼꼼한 계산쟁이 100%", "주인", "109",
                "가계부 요약", "- 오늘 지출: 2건, 합계 12,000원");
    }
}
