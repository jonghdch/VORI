package com.vori.backend.expense;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * 신호 판정 임계값(z_red / z_green) 단일 설정의 조회·수정.
 * ExpenseService 가 판정 시 getConfig() 로 읽고, 어드민이 update() 로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class SignalConfigService {

    static final int CONFIG_ID = 1;
    private static final BigDecimal DEFAULT_Z_RED = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_Z_GREEN = new BigDecimal("1.00");

    private final SignalConfigRepository signalConfigRepository;

    /** 현재 설정. 행이 없으면(이론상 시드되어 있음) 기본값 객체 반환. */
    @Transactional(readOnly = true)
    public SignalConfig getConfig() {
        return signalConfigRepository.findById(CONFIG_ID)
                .orElseGet(() -> SignalConfig.builder()
                        .id(CONFIG_ID)
                        .zRed(DEFAULT_Z_RED)
                        .zGreen(DEFAULT_Z_GREEN)
                        .build());
    }

    /** z_green < z_red 불변식 강제. 위반 시 400. */
    @Transactional
    public SignalConfig update(BigDecimal zRed, BigDecimal zGreen) {
        if (zRed == null || zGreen == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "z_red, z_green 은 필수입니다");
        }
        if (zGreen.compareTo(zRed) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "z_green 은 z_red 보다 작아야 합니다");
        }
        SignalConfig config = signalConfigRepository.findById(CONFIG_ID)
                .orElseGet(() -> SignalConfig.builder().id(CONFIG_ID).build());
        config.update(zRed, zGreen);
        return signalConfigRepository.save(config);
    }
}
