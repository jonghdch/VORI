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

    @Transactional
    public void answerInquiry(Long inquiryId, Long userId, AnswerRequest req) {
        AiInquiry inquiry = aiInquiryRepository.findById(inquiryId)
                .filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다."));

        // 이미 답변된 inquiry 는 재처리 X — 클라이언트 retry·중복 호출 시 Gemini 재호출/signal 재보정 방지
        if (inquiry.getAnsweredAt() != null) return;

        ReasonCategory reason = geminiClient.classifyAnswer(inquiry.getQuestion(), req.answerText());

        Expense expense = expenseRepository.findById(inquiry.getExpenseId()).orElseThrow();
        Signal newSignal = computeSignalFinal(expense.getSignalFinal(), reason);
        boolean adjusted = newSignal != expense.getSignalFinal();

        inquiry.recordAnswer(req.answerText(), reason, adjusted);
        expense.updateSignalFinal(newSignal);
    }

    private Signal computeSignalFinal(Signal original, ReasonCategory reason) {
        return switch (reason) {
            case CEREMONY, EMERGENCY, SELF_INVEST -> Signal.GREEN;
            case SOCIAL -> Signal.GRAY;
            case IMPULSE, ETC -> original;
        };
    }
}
