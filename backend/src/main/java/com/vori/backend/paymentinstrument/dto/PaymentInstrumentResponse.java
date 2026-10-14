package com.vori.backend.paymentinstrument.dto;

import com.vori.backend.paymentinstrument.InstrumentKind;
import com.vori.backend.paymentinstrument.PaymentInstrument;

public record PaymentInstrumentResponse(
        Long id,
        InstrumentKind kind,
        String issuer,
        String name,
        Integer openingBalance
) {
    public static PaymentInstrumentResponse from(PaymentInstrument p) {
        return new PaymentInstrumentResponse(p.getId(), p.getKind(), p.getIssuer(), p.getName(), p.getOpeningBalance());
    }
}
