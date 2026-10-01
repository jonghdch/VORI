package com.vori.backend.ledger;

import com.vori.backend.category.CategoryRepository;
import com.vori.backend.common.PaymentMethod;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.ExpenseService;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import com.vori.backend.expense.dto.ExpenseUpdateRequest;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.income.IncomeService;
import com.vori.backend.income.IncomeSource;
import com.vori.backend.income.dto.IncomeCreateRequest;
import com.vori.backend.income.dto.IncomeResponse;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.ledger.dto.LedgerSaveRequest;
import com.vori.backend.ledger.dto.LedgerSaveRequest.ExpenseEntry;
import com.vori.backend.ledger.dto.LedgerSaveResponse;
import com.vori.backend.savings.SavingService;
import com.vori.backend.savings.SavingType;
import com.vori.backend.savings.dto.SavingCreateRequest;
import com.vori.backend.savings.dto.SavingResponse;
import com.vori.backend.user.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 작성 화면 일괄 저장(POST /api/ledger/entries)이 한 트랜잭션인지 확인한다.
 *
 * 종전에는 화면이 행마다 따로 요청해서, 세 번째 행이 실패해도 앞의 두 행은 저장돼 있었다.
 * 실제 DB 없이 보기 위해 서비스에 Spring 의 트랜잭션 인터셉터를 직접 씌우고, 시작·커밋·롤백
 * 횟수만 세는 트랜잭션 매니저를 붙였다.
 */
class LedgerSaveEntriesTest {
    private static final long USER_ID = 1L;
    private static final LocalDateTime SPENT_AT = LocalDateTime.of(2026, 10, 1, 0, 0);

    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final IncomeService incomeService = mock(IncomeService.class);
    private final SavingService savingService = mock(SavingService.class);
    private final CountingTransactionManager transactions = new CountingTransactionManager();
    private final LedgerService service = transactional(new LedgerService(
            mock(ExpenseRepository.class), mock(IncomeRepository.class), mock(CategoryRepository.class),
            mock(UserRepository.class), mock(AiInquiryRepository.class),
            expenseService, incomeService, savingService));

    @Test
    void failureInLaterRowRollsBackEverything() {
        when(expenseService.createExpense(eq(USER_ID), any())).thenReturn(mock(ExpenseResponse.class));
        when(incomeService.createIncome(eq(USER_ID), any()))
                .thenThrow(new IllegalStateException("수입 저장 실패"));

        assertThrows(IllegalStateException.class, () -> service.saveEntries(USER_ID, new LedgerSaveRequest(
                List.of(newExpense("점심", 8000)), List.of(income()), List.of(saving()))));

        assertEquals(1, transactions.begun, "지출·수입·저축이 한 트랜잭션이어야 한다");
        assertEquals(1, transactions.rolledBack, "앞서 저장한 지출까지 되돌아가야 한다");
        assertEquals(0, transactions.committed);
        verifyNoInteractions(savingService);
    }

    @Test
    void allRowsAreSavedInOneTransactionInRequestOrder() {
        ExpenseResponse created = mock(ExpenseResponse.class);
        ExpenseResponse updated = mock(ExpenseResponse.class);
        IncomeResponse income = mock(IncomeResponse.class);
        SavingResponse saving = mock(SavingResponse.class);
        when(expenseService.createExpense(eq(USER_ID), any())).thenReturn(created);
        when(expenseService.updateExpense(eq(USER_ID), eq(7L), any())).thenReturn(updated);
        when(incomeService.createIncome(eq(USER_ID), any())).thenReturn(income);
        when(savingService.createSaving(eq(USER_ID), any())).thenReturn(saving);

        LedgerSaveResponse result = service.saveEntries(USER_ID, new LedgerSaveRequest(
                List.of(newExpense("점심", 8000), new ExpenseEntry(7L, 3L, 12000, "저녁", SPENT_AT, PaymentMethod.DEBIT)),
                List.of(income()), List.of(saving())));

        assertEquals(List.of(created, updated), result.expenses());
        assertEquals(List.of(income), result.incomes());
        assertEquals(List.of(saving), result.savings());
        assertEquals(1, transactions.begun);
        assertEquals(1, transactions.committed);
        assertEquals(0, transactions.rolledBack);

        // id 없는 행은 등록, 있는 행은 수정 — 단건 API 와 같은 요청으로 넘어간다.
        ArgumentCaptor<ExpenseCreateRequest> createReq = ArgumentCaptor.forClass(ExpenseCreateRequest.class);
        verify(expenseService).createExpense(eq(USER_ID), createReq.capture());
        assertEquals("점심", createReq.getValue().item());
        assertEquals(SPENT_AT, createReq.getValue().spentAt());
        assertTrue(createReq.getValue().timeProvided());
        assertFalse(createReq.getValue().isRecurring());
        ArgumentCaptor<ExpenseUpdateRequest> updateReq = ArgumentCaptor.forClass(ExpenseUpdateRequest.class);
        verify(expenseService).updateExpense(eq(USER_ID), eq(7L), updateReq.capture());
        assertEquals(12000, updateReq.getValue().amount());
    }

    @Test
    void updatingWithoutPaymentMethodIsRejectedLikeTheSingleUpdateApi() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveEntries(USER_ID, new LedgerSaveRequest(
                        List.of(new ExpenseEntry(7L, 3L, 12000, "저녁", SPENT_AT, null)), null, null)));

        assertEquals(400, error.getStatusCode().value());
        assertEquals(1, transactions.rolledBack);
        verifyNoInteractions(expenseService);
    }

    @Test
    void rowsAreValidatedAndMissingListsBecomeEmpty() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        LedgerSaveRequest empty = new LedgerSaveRequest(null, null, null);
        assertTrue(empty.expenses().isEmpty() && empty.incomes().isEmpty() && empty.savings().isEmpty());
        assertTrue(validator.validate(empty).isEmpty());

        assertTrue(validator.validate(new LedgerSaveRequest(
                List.of(newExpense("점심", 8000)), List.of(income()), List.of(saving()))).isEmpty());
        assertFalse(validator.validate(new LedgerSaveRequest(
                List.of(newExpense("점심", 0)), null, null)).isEmpty(), "금액 0 인 지출 행");
        assertFalse(validator.validate(new LedgerSaveRequest(
                List.of(newExpense(" ", 8000)), null, null)).isEmpty(), "내역이 빈 지출 행");
        assertFalse(validator.validate(new LedgerSaveRequest(null,
                List.of(new IncomeCreateRequest(null, 1000, "용돈", LocalDate.now(), null, null)), null)).isEmpty(),
                "출처 없는 수입 행");
    }

    private static ExpenseEntry newExpense(String item, int amount) {
        return new ExpenseEntry(null, 3L, amount, item, SPENT_AT, PaymentMethod.CREDIT);
    }

    private static IncomeCreateRequest income() {
        return new IncomeCreateRequest(IncomeSource.PART_TIME, 50000, "알바", LocalDate.of(2026, 10, 1), null, null);
    }

    private static SavingCreateRequest saving() {
        return new SavingCreateRequest(SavingType.DEPOSIT, 30000, "적금", LocalDate.of(2026, 10, 1), null);
    }

    /** 운영에서 Spring 이 붙이는 것과 같은 @Transactional 인터셉터를 씌운다. */
    private LedgerService transactional(LedgerService target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(
                (TransactionManager) transactions, new AnnotationTransactionAttributeSource()));
        return (LedgerService) factory.getProxy();
    }

    private static class CountingTransactionManager implements PlatformTransactionManager {
        int begun, committed, rolledBack;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begun++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            committed++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rolledBack++;
        }
    }
}
