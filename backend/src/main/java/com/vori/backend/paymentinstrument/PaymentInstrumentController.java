package com.vori.backend.paymentinstrument;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentCreateRequest;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내 카드·계좌 — 환경설정 > 카드·계좌 탭과 지출 기록 화면의 결제 수단 칩이 쓴다. */
@RestController
@RequestMapping("/api/payment-instruments")
@RequiredArgsConstructor
public class PaymentInstrumentController {

    private final PaymentInstrumentService service;

    @GetMapping
    public List<PaymentInstrumentResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return service.list(principal.getId());
    }

    @PostMapping
    public PaymentInstrumentResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentInstrumentCreateRequest req
    ) {
        return service.create(principal.getId(), req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.archive(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
