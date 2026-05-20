package com.vori.backend.inquiry;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseAnomalyEvent;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.inquiry.dto.AnswerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInquiryService {

    private final AiInquiryRepository aiInquiryRepository;
    private final ExpenseRepository expenseRepository;
    private final GeminiClient geminiClient;

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
