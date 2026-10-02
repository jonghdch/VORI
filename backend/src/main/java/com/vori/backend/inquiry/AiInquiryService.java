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
import java.util.List;
import java.util.Map;
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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
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

    /**
     * 커밋 뒤 비동기로 AI 문구를 만들어 대기 질문의 템플릿 문구를 덮어쓴다.
     *
     * 질문 행 자체는 ExpenseService 가 지출과 같은 트랜잭션에서 이미 넣었다(AiInquiry.pending).
     * 그래서 여기서 Gemini 가 실패해도 질문은 남고, 사용자는 템플릿 문구로 답할 수 있다.
     * 행이 없거나(지출 수정으로 지워지고 새 행이 생김) 이미 답한 뒤면 아무것도 바꾸지 않는다.
     * 그래서 이벤트의 inquiryId(행 id)로 찾는다 — expense_id 로 찾으면 수정 전 금액으로 만든
     * 옛 문구가 늦게 도착해 새 질문을 덮어쓴다.
     */
    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAnomalyEvent(ExpenseAnomalyEvent event) {
        try {
            String question = geminiClient.generateQuestion(
                    event.getItem(), event.getAmount(), event.getMeanEma(), event.getStatType()
            );
            refineQuestion(event.getInquiryId(), question);
        } catch (Exception e) {
            // 질문은 템플릿으로 남아 사용자 쪽은 멈추지 않지만, AI 층이 죽은 건 운영이 알아야 한다.
            log.error("AI 질문 문구 생성 실패 — 템플릿 문구 유지: expenseId={}", event.getExpenseId(), e);
        }
    }

    /**
     * 대기 질문의 문구를 AI 문구로 교체. 답한 뒤거나 행이 없으면 false.
     * 엔티티를 읽어 merge 하지 않고 조건 UPDATE 한 번으로 끝낸다 — 그 사이 들어온 답변을
     * 지우지 않기 위해서다. 트랜잭션은 리포지토리 메서드가 연다: 이 메서드는
     * handleAnomalyEvent 가 같은 클래스 안에서 부르므로(self-invocation) 여기에 붙인
     * @Transactional 은 프록시를 타지 않아 효력이 없다.
     */
    public boolean refineQuestion(Long inquiryId, String aiQuestion) {
        if (inquiryId == null || aiQuestion == null || aiQuestion.isBlank()) return false;
        return aiInquiryRepository.updateQuestionIfUnanswered(inquiryId, aiQuestion) > 0;
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
            // 행 잠금으로 읽는다 — 같은 질문에 동시에 온 답변은 여기서 직렬화되고,
            // 뒤엣것은 앞엣것이 커밋한 answeredAt 을 보고 물러난다. 아래 인정 보상이
            // 코인·스탯을 지급하므로 일반 findById 로는 두 번 지급될 수 있다.
            AiInquiry fresh = aiInquiryRepository.findByIdForUpdate(inquiryId).orElseThrow();
            if (fresh.getAnsweredAt() != null) return; // 동시 답변 멱등 가드 재확인

            Expense expense = expenseRepository.findById(fresh.getExpenseId()).orElseThrow();
            Signal newSignal = computeSignalFinal(expense.getSignalFinal(), reason);
            boolean adjusted = newSignal != expense.getSignalFinal();

            fresh.recordAnswer(req.answerText(), reason, adjusted);
            expense.updateSignalFinal(newSignal);
            // 커밋 뒤 펫 칭호(AI 답변 수)를 본다
            eventPublisher.publishEvent(new com.vori.backend.pettitle.PetTitleCheckEvent(userId, "AI_ANSWERED"));
        });
    }

    private Signal computeSignalFinal(Signal original, ReasonCategory reason) {
        return switch (reason) {
            case CEREMONY, EMERGENCY, SELF_INVEST -> Signal.GREEN;
            case SOCIAL -> Signal.GRAY;
            case IMPULSE, ETC -> original;
        };
    }
}
