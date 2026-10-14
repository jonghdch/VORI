package com.vori.backend.paymentinstrument;

import com.vori.backend.paymentinstrument.dto.PaymentInstrumentCreateRequest;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentResponse;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentInstrumentService {

    /** 한 사람이 쓰는 카드·계좌로 넉넉한 수. 이상 입력·남용만 막는다. */
    static final int MAX_ACTIVE = 30;

    private final PaymentInstrumentRepository repository;

    @Transactional(readOnly = true)
    public List<PaymentInstrumentResponse> list(Long userId) {
        return repository.findByUserIdAndArchivedAtIsNullOrderByIdAsc(userId).stream()
                .map(PaymentInstrumentResponse::from)
                .toList();
    }

    @Transactional
    public PaymentInstrumentResponse create(Long userId, PaymentInstrumentCreateRequest req) {
        if (repository.countByUserIdAndArchivedAtIsNull(userId) >= MAX_ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "카드·계좌는 " + MAX_ACTIVE + "개까지 등록할 수 있어요.");
        }
        PaymentInstrument saved = repository.save(PaymentInstrument.builder()
                .userId(userId)
                .kind(req.kind())
                .issuer(req.issuer())
                .name(req.name().trim())
                .openingBalance(balanceFor(req.kind(), req.openingBalance()))
                .build());
        return PaymentInstrumentResponse.from(saved);
    }

    /** 이름·카드사·잔액 수정. 삭제(보관)한 것은 목록에 없으니 고칠 수 없다. */
    @Transactional
    public PaymentInstrumentResponse update(Long userId, Long id, PaymentInstrumentUpdateRequest req) {
        PaymentInstrument instrument = requireOwned(userId, id);
        if (instrument.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제한 카드·계좌는 고칠 수 없어요.");
        }
        instrument.updateDetails(req.issuer(), req.name().trim(), balanceFor(instrument.getKind(), req.openingBalance()));
        return PaymentInstrumentResponse.from(instrument);
    }

    /** 카드는 잔액이 없다 — 보내도 0. 계좌·예적금·현금은 비우면 0. */
    private static int balanceFor(InstrumentKind kind, Integer openingBalance) {
        boolean hasBalance = kind != InstrumentKind.DEBIT && kind != InstrumentKind.CREDIT;
        return hasBalance && openingBalance != null ? openingBalance : 0;
    }

    /** 삭제 = 보관. 이미 이 카드로 적은 지출은 연결을 그대로 둔다. */
    @Transactional
    public void archive(Long userId, Long id) {
        requireOwned(userId, id).archive(LocalDateTime.now());
    }

    /**
     * 수입을 받을 곳으로 쓸 수 있는지 확인한다 — 본인 것 · 삭제(보관) 안 됨 · 신용카드가 아님.
     * 체크카드는 연결된 통장으로 받는 것이라 고를 수 있다. 신용카드로는 수입을 받지 않는다.
     */
    @Transactional(readOnly = true)
    public PaymentInstrument requireIncomeAccount(Long userId, Long id) {
        PaymentInstrument instrument = requireOwned(userId, id);
        if (instrument.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제한 카드·계좌예요. 다른 곳을 골라 주세요.");
        }
        if (instrument.getKind() == InstrumentKind.CREDIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신용카드로는 수입을 받을 수 없어요.");
        }
        return instrument;
    }

    /**
     * 본인 카드·계좌인지 확인하고 돌려준다. 보관한 것도 돌려준다 — 예전 지출을 고칠 때 그 연결을 지키려고.
     * 남의 것이면 있는지조차 알리지 않으려고 404.
     */
    @Transactional(readOnly = true)
    public PaymentInstrument requireOwned(Long userId, Long id) {
        return repository.findById(id)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드·계좌를 찾을 수 없어요."));
    }
}
