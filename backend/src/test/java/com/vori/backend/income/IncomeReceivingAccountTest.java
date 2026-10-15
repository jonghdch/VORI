package com.vori.backend.income;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.income.dto.IncomeCreateRequest;
import com.vori.backend.income.dto.IncomeResponse;
import com.vori.backend.paymentinstrument.InstrumentKind;
import com.vori.backend.paymentinstrument.PaymentInstrument;
import com.vori.backend.paymentinstrument.PaymentInstrumentRepository;
import com.vori.backend.paymentinstrument.PaymentInstrumentService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 수입의 받은 곳 — 본인의 입출금·체크카드·예적금·현금 (신용카드 X), 삭제한 것은 안 됨. */
class IncomeReceivingAccountTest {
    private static final long USER_ID = 1L;

    private final IncomeRepository incomes = mock(IncomeRepository.class);
    private final PaymentInstrumentRepository instruments = mock(PaymentInstrumentRepository.class);
    private final IncomeService service = new IncomeService(incomes, new PaymentInstrumentService(instruments));

    private void stub(long id, long owner, InstrumentKind kind, boolean archived) {
        PaymentInstrument p = PaymentInstrument.builder().id(id).userId(owner).kind(kind).issuer("TOSSBANK").name("통장").build();
        if (archived) p.archive(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(instruments.findById(id)).thenReturn(Optional.of(p));
    }

    private IncomeCreateRequest req(Long instrumentId) {
        return new IncomeCreateRequest(IncomeSource.PART_TIME, 50000, "알바비", LocalDate.of(2026, 10, 10), null, null, instrumentId);
    }

    @Test
    void checkingAccountIsLinkedAndFillsTransfer() {
        stub(3L, USER_ID, InstrumentKind.CHECKING, false);
        when(incomes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse res = service.createIncome(USER_ID, req(3L));

        assertEquals(3L, res.paymentInstrumentId());
        assertEquals(PaymentMethod.TRANSFER, res.paymentMethod());
    }

    @Test
    void debitCardMeansItsLinkedAccount() {
        stub(1L, USER_ID, InstrumentKind.DEBIT, false);
        when(incomes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse res = service.createIncome(USER_ID, req(1L));

        assertEquals(1L, res.paymentInstrumentId());
        assertEquals(PaymentMethod.TRANSFER, res.paymentMethod(), "체크카드 연결 통장으로 받은 것");
    }

    @Test
    void chosenAccountOverridesAMismatchedPaymentMethod() {
        // 현금을 받은 곳으로 골라 놓고 신용카드라고 보내도 현금으로 저장 — "현금으로 받은 신용카드 수입" 방지
        stub(4L, USER_ID, InstrumentKind.CASH, false);
        when(incomes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse res = service.createIncome(USER_ID, new IncomeCreateRequest(
                IncomeSource.PART_TIME, 50000, "알바비", LocalDate.of(2026, 10, 10), PaymentMethod.CREDIT, null, 4L));

        assertEquals(PaymentMethod.CASH, res.paymentMethod());
    }

    @Test
    void savingsAccountKeepsTheMethodTheScreenSent() {
        // 예적금은 결제수단 종류가 없어 덮어쓰지 않는다
        stub(5L, USER_ID, InstrumentKind.SAVINGS, false);
        when(incomes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse res = service.createIncome(USER_ID, new IncomeCreateRequest(
                IncomeSource.PART_TIME, 50000, "이자", LocalDate.of(2026, 10, 10), PaymentMethod.TRANSFER, null, 5L));

        assertEquals(PaymentMethod.TRANSFER, res.paymentMethod());
    }

    @Test
    void noAccountStaysAsBefore() {
        when(incomes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse res = service.createIncome(USER_ID, req(null));

        assertNull(res.paymentInstrumentId());
        assertNull(res.paymentMethod());
    }

    @Test
    void cardsArchivedAndOthersAccountsAreRejected() {
        stub(4L, USER_ID, InstrumentKind.CREDIT, false);
        stub(5L, USER_ID, InstrumentKind.CASH, true);
        stub(9L, 99L, InstrumentKind.CHECKING, false);

        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.createIncome(USER_ID, req(4L)))
                .getStatusCode().value(), "신용카드로는 수입을 받지 않는다");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.createIncome(USER_ID, req(5L)))
                .getStatusCode().value(), "삭제한 현금 지갑");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.createIncome(USER_ID, req(9L)))
                .getStatusCode().value(), "남의 계좌");
        verify(incomes, never()).save(any());
    }
}
