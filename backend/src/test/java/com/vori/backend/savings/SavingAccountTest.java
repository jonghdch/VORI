package com.vori.backend.savings;

import com.vori.backend.paymentinstrument.InstrumentKind;
import com.vori.backend.paymentinstrument.PaymentInstrument;
import com.vori.backend.paymentinstrument.PaymentInstrumentRepository;
import com.vori.backend.paymentinstrument.PaymentInstrumentService;
import com.vori.backend.savings.dto.SavingCreateRequest;
import com.vori.backend.savings.dto.SavingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 저축의 모은 곳 — 예적금 유형에서, 본인의 삭제 안 한 저축 통장만. */
class SavingAccountTest {
    private static final long USER_ID = 1L;

    private final SavingRepository savings = mock(SavingRepository.class);
    private final PaymentInstrumentRepository instruments = mock(PaymentInstrumentRepository.class);
    private final SavingService service = new SavingService(savings, new PaymentInstrumentService(instruments));

    private void stub(long id, long owner, InstrumentKind kind, boolean archived) {
        PaymentInstrument p = PaymentInstrument.builder().id(id).userId(owner).kind(kind).issuer("TOSSBANK").name("토스저축").build();
        if (archived) p.archive(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(instruments.findById(id)).thenReturn(Optional.of(p));
    }

    private SavingCreateRequest req(SavingType type, Long instrumentId) {
        return new SavingCreateRequest(type, 50000, "적금 넣기", LocalDate.of(2026, 10, 10), null, instrumentId);
    }

    @Test
    void depositIntoOwnSavingsAccountIsLinked() {
        stub(4L, USER_ID, InstrumentKind.SAVINGS, false);
        when(savings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingResponse res = service.createSaving(USER_ID, req(SavingType.DEPOSIT, 4L));

        assertEquals(4L, res.paymentInstrumentId());
    }

    @Test
    void noAccountAndInvestWithoutAccountStayAsBefore() {
        when(savings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertNull(service.createSaving(USER_ID, req(SavingType.DEPOSIT, null)).paymentInstrumentId());
        assertNull(service.createSaving(USER_ID, req(SavingType.INVEST, null)).paymentInstrumentId());
    }

    @Test
    void rejectsInvestNonSavingsArchivedAndOthers() {
        stub(4L, USER_ID, InstrumentKind.SAVINGS, false);
        stub(1L, USER_ID, InstrumentKind.DEBIT, false);
        stub(5L, USER_ID, InstrumentKind.SAVINGS, true);
        stub(9L, 99L, InstrumentKind.SAVINGS, false);

        assertEquals(400, status(() -> service.createSaving(USER_ID, req(SavingType.INVEST, 4L))), "투자는 모은 곳 없음");
        assertEquals(400, status(() -> service.createSaving(USER_ID, req(SavingType.DEPOSIT, 1L))), "체크카드는 저축 통장이 아님");
        assertEquals(400, status(() -> service.createSaving(USER_ID, req(SavingType.DEPOSIT, 5L))), "삭제한 통장");
        assertEquals(404, status(() -> service.createSaving(USER_ID, req(SavingType.DEPOSIT, 9L))), "남의 통장");
        verify(savings, never()).save(any());
    }

    private static int status(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(ResponseStatusException.class, call).getStatusCode().value();
    }
}
