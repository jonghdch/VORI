package com.vori.backend.paymentinstrument;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.expense.Expense;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentCreateRequest;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 내 카드·계좌 등록·보관 규칙 — DB 없이 저장소만 흉내 낸다. */
class PaymentInstrumentServiceTest {
    private static final long USER_ID = 1L;

    private final PaymentInstrumentRepository repository = mock(PaymentInstrumentRepository.class);
    private final PaymentInstrumentService service = new PaymentInstrumentService(repository);

    private PaymentInstrumentResponse create(InstrumentKind kind, String name, Integer balance) {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return service.create(USER_ID, new PaymentInstrumentCreateRequest(kind, "TOSSBANK", name, balance));
    }

    @Test
    void cardsIgnoreBalanceAndNamesAreTrimmed() {
        PaymentInstrumentResponse card = create(InstrumentKind.DEBIT, "  토스뱅크 체크카드 ", 50000);
        assertEquals("토스뱅크 체크카드", card.name());
        assertEquals(0, card.openingBalance(), "카드는 잔액이 없다");

        PaymentInstrumentResponse account = create(InstrumentKind.CHECKING, "생활통장", 27458);
        assertEquals(27458, account.openingBalance());
        assertEquals(0, create(InstrumentKind.CASH, "현금", null).openingBalance(), "비우면 0");
    }

    @Test
    void stopsAtTheActiveLimit() {
        when(repository.countByUserIdAndArchivedAtIsNull(USER_ID)).thenReturn((long) PaymentInstrumentService.MAX_ACTIVE);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(USER_ID, new PaymentInstrumentCreateRequest(InstrumentKind.CREDIT, "KB", "카드", null)));

        assertEquals(400, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void archiveOnlyMarksOwnInstrument() {
        PaymentInstrument mine = PaymentInstrument.builder().id(4L).userId(USER_ID).kind(InstrumentKind.CREDIT)
                .issuer("KBANK").name("K-패스").build();
        PaymentInstrument theirs = PaymentInstrument.builder().id(9L).userId(99L).kind(InstrumentKind.DEBIT)
                .issuer("KB").name("남의 카드").build();
        when(repository.findById(4L)).thenReturn(Optional.of(mine));
        when(repository.findById(9L)).thenReturn(Optional.of(theirs));

        service.archive(USER_ID, 4L);
        assertNotNull(mine.getArchivedAt());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.archive(USER_ID, 9L));
        assertEquals(404, error.getStatusCode().value(), "남의 카드는 있는지조차 알리지 않는다");
        assertNull(theirs.getArchivedAt());
    }

    @Test
    void archivingTwiceKeepsTheFirstTime() {
        PaymentInstrument p = PaymentInstrument.builder().id(4L).userId(USER_ID).kind(InstrumentKind.CREDIT)
                .issuer("KBANK").name("K-패스").build();
        LocalDateTime first = LocalDateTime.of(2026, 10, 1, 9, 0);
        p.archive(first);
        p.archive(first.plusDays(1));
        assertEquals(first, p.getArchivedAt());
    }

    @Test
    void everyKindMapsToTheExpensePaymentMethod() {
        assertEquals(PaymentMethod.DEBIT, InstrumentKind.DEBIT.paymentMethod());
        assertEquals(PaymentMethod.CREDIT, InstrumentKind.CREDIT.paymentMethod());
        assertEquals(PaymentMethod.TRANSFER, InstrumentKind.CHECKING.paymentMethod());
        assertEquals(PaymentMethod.CASH, InstrumentKind.CASH.paymentMethod());
        assertNull(InstrumentKind.SAVINGS.paymentMethod(), "예적금은 결제수단이 아니다");
    }

    @Test
    void changingAnExpensesKindDropsItsCard() {
        Expense e = Expense.builder().id(7L).userId(USER_ID).item("저녁").amount(12000).categoryId(3L)
                .paymentMethod(PaymentMethod.DEBIT).paymentInstrumentId(5L).spentAt(LocalDateTime.now()).build();

        e.updatePaymentMethod(PaymentMethod.DEBIT);
        assertEquals(5L, e.getPaymentInstrumentId(), "같은 종류면 카드 유지");

        e.updatePaymentMethod(PaymentMethod.CASH);
        assertNull(e.getPaymentInstrumentId(), "종류가 바뀌면 다른 카드라는 뜻");
    }
}
