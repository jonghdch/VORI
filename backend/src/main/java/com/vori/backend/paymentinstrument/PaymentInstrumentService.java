package com.vori.backend.paymentinstrument;

import com.vori.backend.paymentinstrument.dto.PaymentInstrumentCreateRequest;
import com.vori.backend.paymentinstrument.dto.PaymentInstrumentResponse;
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
        boolean hasBalance = req.kind() != InstrumentKind.DEBIT && req.kind() != InstrumentKind.CREDIT;
        PaymentInstrument saved = repository.save(PaymentInstrument.builder()
                .userId(userId)
                .kind(req.kind())
                .issuer(req.issuer())
                .name(req.name().trim())
                .openingBalance(hasBalance && req.openingBalance() != null ? req.openingBalance() : 0)
                .build());
        return PaymentInstrumentResponse.from(saved);
    }

    /** 삭제 = 보관. 이미 이 카드로 적은 지출은 연결을 그대로 둔다. */
    @Transactional
    public void archive(Long userId, Long id) {
        requireOwned(userId, id).archive(LocalDateTime.now());
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
