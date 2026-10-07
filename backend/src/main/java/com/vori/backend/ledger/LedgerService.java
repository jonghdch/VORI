package com.vori.backend.ledger;

import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.ExpenseService;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import com.vori.backend.expense.dto.ExpenseUpdateRequest;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.income.IncomeService;
import com.vori.backend.income.dto.IncomeResponse;
import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.ledger.dto.LedgerSaveRequest;
import com.vori.backend.ledger.dto.LedgerSaveResponse;
import com.vori.backend.savings.SavingService;
import com.vori.backend.savings.dto.SavingResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AiInquiryRepository aiInquiryRepository;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final SavingService savingService;

    /** 해당 월(yyyy-MM) 의 본인 지출+수입을 날짜 오름차순으로 병합. */
    @Transactional(readOnly = true)
    public List<LedgerResponse> getMonthly(Long userId, String yearMonth) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(yearMonth);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "yearMonth 형식은 yyyy-MM 입니다");
        }

        Map<Long, String> categoryNames = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categoryNames.put(c.getId(), c.getName()));

        List<Expense> expenses = expenseRepository.findByUserIdAndSpentAtBetween(
                userId, ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59));

        // 이 달 지출 중 AI 질문(판정)을 거친 것 = 예외적 지출.
        // aiJudged 배지 + 답변된 소비 사유(answerText)를 함께 내려준다.
        Map<Long, AiInquiry> inquiryByExpenseId = expenses.isEmpty() ? Map.of()
                : aiInquiryRepository.findByExpenseIdIn(
                                expenses.stream().map(Expense::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                AiInquiry::getExpenseId, i -> i, (a, b) -> a));

        List<LedgerResponse> rows = new ArrayList<>();
        expenses.forEach(e -> {
            AiInquiry inquiry = inquiryByExpenseId.get(e.getId());
            rows.add(LedgerResponse.expense(
                    e,
                    categoryNames.getOrDefault(e.getCategoryId(), "기타"),
                    inquiry != null,
                    inquiry != null ? inquiry.getAnswerText() : null));
        });
        incomeRepository
                .findByUserIdAndReceivedAtBetween(userId, ym.atDay(1), ym.atEndOfMonth())
                .forEach(i -> rows.add(LedgerResponse.income(i)));

        rows.sort(Comparator.comparing(LedgerResponse::date));
        return rows;
    }

    /**
     * 작성 화면의 지출·수입·저축을 한 트랜잭션으로 저장한다.
     *
     * 화면이 행마다 따로 요청하면 중간에 하나가 실패했을 때 앞의 행은 이미 저장돼 있다.
     * 사용자는 "저장 실패" 를 보는데 DB 에는 일부가 들어가 있고, 그 행들의 EMA·누적 절약액도
     * 이미 반영된 상태다. 여기서 묶으면 하나라도 실패할 때 전부 되돌아간다.
     *
     * 각 행의 처리는 단건 API 가 쓰는 서비스에 그대로 맡긴다 — 규칙이 두 곳으로 갈라지지 않는다.
     * 그 서비스들의 @Transactional 은 이 트랜잭션에 합류하고, AI 질문·칭호 이벤트는 커밋 뒤에
     * 나가므로 되돌아간 저장에 대해서는 나가지 않는다.
     */
    @Transactional
    public LedgerSaveResponse saveEntries(Long userId, LedgerSaveRequest req) {
        List<ExpenseResponse> expenses = new ArrayList<>();
        for (LedgerSaveRequest.ExpenseEntry e : req.expenses()) {
            expenses.add(e.id() == null ? createExpense(userId, e) : updateExpense(userId, e));
        }
        List<IncomeResponse> incomes = req.incomes().stream()
                .map(i -> incomeService.createIncome(userId, i))
                .toList();
        List<SavingResponse> savings = req.savings().stream()
                .map(s -> savingService.createSaving(userId, s))
                .toList();
        return new LedgerSaveResponse(expenses, incomes, savings);
    }

    private ExpenseResponse createExpense(Long userId, LedgerSaveRequest.ExpenseEntry e) {
        return expenseService.createExpense(userId, new ExpenseCreateRequest(
                e.categoryId(), e.amount(), e.item(), e.spentAt(), null, e.paymentMethod(), normalizeMemo(e.memo()), null));
    }

    private ExpenseResponse updateExpense(Long userId, LedgerSaveRequest.ExpenseEntry e) {
        // 단건 수정(ExpenseUpdateRequest)은 결제수단이 필수다. 여기서는 등록과 한 목록이라 직접 본다.
        if (e.paymentMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제수단을 선택해주세요.");
        }
        Expense current = expenseRepository.findById(e.id())
                .orElseThrow(() -> new IllegalArgumentException("지출을 찾을 수 없습니다."));
        if (!current.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 지출만 수정할 수 있습니다.");
        }
        // 메모는 보냈을 때만 바꾼다(null = 그대로, 빈 글자 = 지움). 판정에 안 쓰니 다시 계산할 것도 없다.
        if (e.memo() != null) current.updateMemo(normalizeMemo(e.memo()));
        // 내역·금액·카테고리가 그대로면 다시 판정하지 않는다. ExpenseService.updateExpense 는 판정을 다시 내면서
        // 이 지출의 AI 질문과 답변을 지우고 새로 만든다 — 메모·결제수단만 고친 수정이 답변을 날리면 안 된다.
        // 결제수단은 판정 계산·AI 질문 문구에 쓰지 않아 여기서 그대로 바꾼다. 결제수단이 비어 있던 예전 지출은
        // 화면이 기본값(신용카드)을 채워 보내는데, 그것도 다시 판정할 이유가 아니다.
        if (sameJudgedFields(current, e)) {
            if (current.getPaymentMethod() != e.paymentMethod()) current.updatePaymentMethod(e.paymentMethod());
            return ExpenseResponse.from(current);
        }
        return expenseService.updateExpense(userId, e.id(), new ExpenseUpdateRequest(
                e.item(), e.amount(), e.categoryId(), e.paymentMethod()));
    }

    /** 다시 판정할 필요가 없는지 — 내역(AI 질문 문구에 들어감)·금액·카테고리가 저장된 값과 같다. */
    private static boolean sameJudgedFields(Expense current, LedgerSaveRequest.ExpenseEntry e) {
        // 다른 경로로 만든 지출은 내역 끝에 공백이 남아 있을 수 있어 양쪽 다 다듬어 비교한다
        return current.getItem().trim().equals(e.item().trim())
                && current.getAmount().equals(e.amount())
                && current.getCategoryId().equals(e.categoryId());
    }

    /** 메모 앞뒤 공백을 지우고, 비면 null(메모 없음). */
    static String normalizeMemo(String memo) {
        if (memo == null) return null;
        String trimmed = memo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 본인 지출만 삭제. 없으면 404, 남의 것이면 403.
     *
     * 파생 상태 처리 (도메인 규칙):
     * - user.totalSaved — 이 지출이 더했던 절약액을 되돌린다 (사용자에게 보이는 누적 수치).
     * - EMA(user_stat_stats)·펫 스탯·goal 누적 — 의도적으로 보존. EMA 는 시계열 지표라
     *   중간 항 제거가 수학적으로 불가하고, 펫 성장·goal 은 "그 시점에 일어난 이력" 으로 취급.
     */
    @Transactional
    public void deleteExpense(Long userId, Long expenseId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지출을 찾을 수 없습니다"));
        if (!e.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 지출만 삭제할 수 있습니다");
        }

        int saved = e.getSavedAmount() == null ? 0 : e.getSavedAmount();
        if (saved > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            int current = user.getTotalSaved() == null ? 0 : user.getTotalSaved();
            user.addTotalSaved(-Math.min(saved, current)); // 음수 방지 클램프
        }

        expenseRepository.delete(e);
    }
}
