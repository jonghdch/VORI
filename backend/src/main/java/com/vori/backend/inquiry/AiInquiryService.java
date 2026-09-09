package com.vori.backend.inquiry;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseAnomalyEvent;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.inquiry.dto.AnswerRequest;
import com.vori.backend.inquiry.dto.InquiryResponse;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLog;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInquiryService {

    private final AiInquiryRepository aiInquiryRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final GeminiClient geminiClient;
    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;

    /**
     * 인정 보상 — 피할 수 없었거나 미래를 위한 지출로 판정되면 주는 위로 보상.
     *
     * 이게 없으면 "합리적이었다" 고 인정받아도 코인·스탯이 0 이라, AI 에게 이유를 설명할
     * 이유가 없어진다(신호등 색만 바뀐다). 반대로 절약 보상(1,000원당 스탯 1)에 가깝게
     * 주면 "변명하는 게 이득" 이 되므로 훨씬 작게 잡는다 — 경조사 5번을 인정받아도
     * 스탯 50 으로, 5만원 절약 한 번(스탯 50)과 같은 수준이다.
     *
     * 감점이 아니라 가산인 이유는 세 가지다. 과지출은 애초에 감점이 아니라 무보상이고
     * (stat_delta = max(saved,0)), 코인은 이미 썼을 수 있어 회수하면 잔액이 음수가 되며,
     * expenses.stat_delta 가 INT UNSIGNED 라 음수를 기록할 수도 없다.
     */
    private static final int RECOGNITION_STAT = 10;
    private static final int RECOGNITION_COIN = 100;

    /**
     * 인정 대상 사유. SOCIAL(사회생활)은 뺐다 — "회식이었어요" 로 매번 보상받으면
     * 판정이 핑계 대기 게임이 된다. 피할 수 없던 지출(경조사·긴급)과 미래를 위한
     * 지출(자기투자)만 인정한다.
     */
    private static final Set<ReasonCategory> RECOGNIZED_REASONS =
            EnumSet.of(ReasonCategory.CEREMONY, ReasonCategory.EMERGENCY, ReasonCategory.SELF_INVEST);

    /** 특정 날짜의 미답변 inquiry 목록 — Step 2 화면용. */
    @Transactional(readOnly = true)
    public List<InquiryResponse> listPendingByDate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<AiInquiry> inquiries = aiInquiryRepository.findPendingByDate(userId, start, end);
        if (inquiries.isEmpty()) return List.of();

        List<Long> expenseIds = inquiries.stream().map(AiInquiry::getExpenseId).toList();
        Map<Long, Expense> expById = expenseRepository.findAllById(expenseIds).stream()
                .collect(Collectors.toMap(Expense::getId, Function.identity()));

        List<Long> categoryIds = expById.values().stream().map(Expense::getCategoryId).distinct().toList();
        Map<Long, Category> catById = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        // parent name 도 같이 조회 (소분류 → 대분류)
        List<Long> parentIds = catById.values().stream()
                .map(Category::getParentId).filter(p -> p != null).distinct().toList();
        Map<Long, Category> parentById = parentIds.isEmpty() ? Map.of() :
                categoryRepository.findAllById(parentIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));

        return inquiries.stream()
                .map(i -> {
                    Expense e = expById.get(i.getExpenseId());
                    Category c = e != null ? catById.get(e.getCategoryId()) : null;
                    Category p = (c != null && c.getParentId() != null) ? parentById.get(c.getParentId()) : null;
                    return new InquiryResponse(
                            i.getId(),
                            i.getQuestion(),
                            e != null ? e.getId() : null,
                            e != null ? e.getItem() : null,
                            e != null ? e.getAmount() : null,
                            e != null ? e.getPaymentMethod() : null,
                            c != null ? c.getId() : null,
                            c != null ? c.getName() : null,
                            p != null ? p.getName() : null
                    );
                })
                .toList();
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAnomalyEvent(ExpenseAnomalyEvent event) {
        try {
            String question = geminiClient.generateQuestion(
                    event.getItem(), event.getAmount(), event.getMeanEma(), event.getStatType()
            );
            aiInquiryRepository.save(AiInquiry.builder()
                    .expenseId(event.getExpenseId())
                    .userId(event.getUserId())
                    .question(question)
                    .signalAdjusted(false)
                    .askedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("AI 질문 생성 실패: expenseId={}", event.getExpenseId(), e);
        }
    }

    /**
     * 외부 API(Gemini, read timeout 최대 15s)를 트랜잭션 밖에서 호출한다.
     * 메서드 전체에 @Transactional 을 걸면 응답 대기 동안 DB 커넥션을 점유 →
     * 동시 답변 몇 건이면 커넥션 풀 고갈. 검증(읽기) → Gemini → 짧은 쓰기 순으로 분리.
     */
    public void answerInquiry(Long inquiryId, Long userId, AnswerRequest req) {
        // 1) 검증 — 짧은 읽기 (repo 자체 트랜잭션)
        AiInquiry inquiry = aiInquiryRepository.findById(inquiryId)
                .filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다."));

        // 이미 답변된 inquiry 는 재처리 X — 클라이언트 retry·중복 호출 시 Gemini 재호출/signal 재보정 방지
        if (inquiry.getAnsweredAt() != null) return;

        // 2) 외부 API — 트랜잭션·커넥션 비점유 상태로 호출
        ReasonCategory reason = geminiClient.classifyAnswer(inquiry.getQuestion(), req.answerText());

        // 3) 짧은 쓰기 트랜잭션
        transactionTemplate.executeWithoutResult(tx -> {
            AiInquiry fresh = aiInquiryRepository.findById(inquiryId).orElseThrow();
            if (fresh.getAnsweredAt() != null) return; // 동시 답변 멱등 가드 재확인

            Expense expense = expenseRepository.findById(fresh.getExpenseId()).orElseThrow();
            Signal newSignal = computeSignalFinal(expense.getSignalFinal(), reason);
            boolean adjusted = newSignal != expense.getSignalFinal();

            fresh.recordAnswer(req.answerText(), reason, adjusted);
            expense.updateSignalFinal(newSignal);
            grantRecognitionBonus(userId, expense, reason);
        });
    }

    /**
     * 합리적 과지출로 인정된 경우에만 보상한다.
     *
     * 절약한 지출(saved_amount >= 0)은 등록 시점에 이미 보상을 받았으므로 제외한다.
     * 중복 지급은 호출부의 answeredAt 멱등 가드가 막는다 — 같은 inquiry 는 한 번만 여기 온다.
     */
    private void grantRecognitionBonus(Long userId, Expense expense, ReasonCategory reason) {
        if (!RECOGNIZED_REASONS.contains(reason)) return;

        Integer saved = expense.getSavedAmount();
        if (saved == null || saved >= 0) return;

        // 잔액 변경 경로라 행을 잠그고 읽는다 (가구·알 구매와 동일)
        userRepository.findByIdForUpdate(userId)
                .ifPresent(u -> u.addGameMoney(RECOGNITION_COIN));

        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) {
            // 펫이 없어도 코인은 준다. 분양 직후 답변하는 경우가 있다.
            log.info("인정 보상 — 코인만 지급(활성 펫 없음). userId={}, expenseId={}, reason={}",
                    userId, expense.getId(), reason);
            return;
        }

        Pet pet = pets.get(0);
        pet.addStat(expense.getStatType(), RECOGNITION_STAT);
        pet.evaluateStage();

        petGrowthLogRepository.save(PetGrowthLog.builder()
                .petId(pet.getId())
                .userId(userId)
                .expenseId(expense.getId())
                .statType(expense.getStatType())
                .delta(RECOGNITION_STAT)
                // 절약해서 얻은 게 아니므로 0. 흔적은 reason=BONUS 로 구분한다.
                .savedAmount(0)
                .reason(GrowthReason.BONUS)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("인정 보상 지급 — userId={}, expenseId={}, reason={}, 스탯+{}, 코인+{}",
                userId, expense.getId(), reason, RECOGNITION_STAT, RECOGNITION_COIN);
    }

    private Signal computeSignalFinal(Signal original, ReasonCategory reason) {
        return switch (reason) {
            case CEREMONY, EMERGENCY, SELF_INVEST -> Signal.GREEN;
            case SOCIAL -> Signal.GRAY;
            case IMPULSE, ETC -> original;
        };
    }
}
